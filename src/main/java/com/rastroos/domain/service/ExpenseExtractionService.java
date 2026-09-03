package com.rastroos.domain.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rastroos.config.ExtractionProperties;
import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.InvalidUploadException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.web.dto.ExtractedExpense;

/**
 * Extrai campos de um lançamento a partir de um documento (PDF/imagem) ou de
 * uma foto da notinha. O resultado é sempre uma <strong>sugestão editável</strong>
 * — o usuário valida e ajusta antes de salvar (a criação da transação continua
 * passando pela validação normal do {@code TransactionForm}).
 *
 * <p>Por padrão roda em <strong>modo demonstração</strong> (stub): não há
 * {@code extraction.base-url} configurado, então devolvemos campos-base
 * (descrição do arquivo, vencimento = hoje) e marcamos {@code demo = true}.
 * Configurando o endpoint, a leitura real por IA de visão entra no lugar
 * (próximo passo), reaproveitando toda a validação/segurança de upload abaixo.
 *
 * <p>Segurança de upload (§3.2): valida arquivo não-vazio, tamanho máximo,
 * tipo de conteúdo, extensão (allow-list) e assinatura (magic bytes). O arquivo
 * é processado em memória e <strong>nunca persistido</strong> (privacidade).
 */
@Service
public class ExpenseExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseExtractionService.class);

    private static final int MAX_DESCRIPTION = 200;

    private static final Set<String> DOCUMENT_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/webp");
    private static final Set<String> DOCUMENT_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "webp");
    private static final Set<String> RECEIPT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif");
    private static final Set<String> RECEIPT_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "heic", "heif");

    private final ExtractionProperties props;
    private final AccountRepository accounts;
    private final Clock clock;

    public ExpenseExtractionService(ExtractionProperties props, AccountRepository accounts, Clock clock) {
        this.props = props;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Extrai (ou sugere) os campos do gasto. Lança {@link InvalidUploadException}
     * quando o arquivo é inválido — a camada Web mostra a mensagem no formulário.
     */
    public ExtractedExpense extract(UUID userId, MultipartFile file, ExpenseExtractionSource source) {
        validate(file, source);

        if (props.isEnabled()) {
            // Costura para a IA de visão real: quando o cliente multimodal for
            // implementado, ele substitui o stub aqui. Por ora, degrada para o stub.
            log.info("extraction.base-url configurado, mas o cliente de visão ainda não foi "
                    + "implementado; usando extração em modo demonstração.");
        }

        ExtractedExpense base = stub(file, source);
        UUID accountId = matchAccountByLast4(userId, base.last4());
        if (accountId == null) {
            return base;
        }
        return new ExtractedExpense(base.description(), base.amount(), base.dueDate(), base.fixed(),
                base.categoryId(), base.last4(), accountId, base.demo());
    }

    /** Extração de demonstração: campos-base editáveis, sem inventar valores monetários. */
    private ExtractedExpense stub(MultipartFile file, ExpenseExtractionSource source) {
        LocalDate today = LocalDate.now(clock);
        if (source == ExpenseExtractionSource.RECEIPT) {
            // A leitura real traria valor, data/hora e os 4 dígitos do cartão.
            return new ExtractedExpense("Compra no cartão", null, today, false, null, null, null, true);
        }
        return new ExtractedExpense(descriptionFromFilename(file.getOriginalFilename()),
                null, today, false, null, null, null, true);
    }

    /** Casa os 4 dígitos lidos com um cartão do usuário; {@code null} se não bater. */
    UUID matchAccountByLast4(UUID userId, String last4) {
        if (last4 == null || last4.isBlank()) {
            return null;
        }
        String digits = last4.trim();
        return accounts.findAllByUserIdOrderByNameAsc(userId).stream()
                .filter(a -> a.getKind() == AccountKind.CARD && digits.equals(a.getLast4()))
                .map(Account::getId)
                .findFirst()
                .orElse(null);
    }

    private void validate(MultipartFile file, ExpenseExtractionSource source) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("transaction.extract.empty");
        }
        if (file.getSize() > props.getMaxFileSizeBytes()) {
            throw new InvalidUploadException("transaction.extract.tooLarge");
        }

        Set<String> allowedTypes = source == ExpenseExtractionSource.RECEIPT ? RECEIPT_TYPES : DOCUMENT_TYPES;
        Set<String> allowedExts = source == ExpenseExtractionSource.RECEIPT ? RECEIPT_EXTENSIONS : DOCUMENT_EXTENSIONS;

        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !allowedExts.contains(ext)) {
            throw new InvalidUploadException("transaction.extract.badType");
        }

        // Defesa em profundidade: assinatura do arquivo não pode contradizer o
        // tipo declarado (ex.: PDF disfarçado de imagem). Formatos que não
        // sabemos "farejar" (heic/heif) passam pelo tipo+extensão.
        String sniffed = sniff(file);
        if (sniffed != null && !allowedTypes.contains(sniffed)) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase();
        return base.isEmpty() ? null : base;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /** Detecta o tipo pelos primeiros bytes; {@code null} se não reconhecer. */
    private static String sniff(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
        if (read >= 4 && head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46) {
            return "application/pdf"; // %PDF
        }
        if (read >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return "image/png";
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String descriptionFromFilename(String filename) {
        String ext = extensionOf(filename);
        String name = filename == null ? "" : filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (ext != null && name.toLowerCase().endsWith("." + ext)) {
            name = name.substring(0, name.length() - ext.length() - 1);
        }
        name = name.replace('_', ' ').replace('-', ' ').trim();
        if (name.isEmpty()) {
            return "Documento importado";
        }
        return name.length() > MAX_DESCRIPTION ? name.substring(0, MAX_DESCRIPTION) : name;
    }
}
