package com.rastroos.domain.service;

import java.math.BigDecimal;
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
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.web.dto.ExtractedExpense;

/**
 * Extrai campos de um lançamento a partir de um documento (PDF/imagem) ou de
 * uma foto da notinha. O resultado é sempre uma <strong>sugestão editável</strong>
 * — o usuário valida e ajusta antes de salvar (a criação da transação continua
 * passando pela validação normal do {@code TransactionForm}).
 *
 * <p>Com a IA configurada, {@link ExpenseVisionReader} lê o arquivo de verdade
 * e devolve valor, data, descrição e os 4 dígitos do cartão. Sem IA — ou se o
 * provedor falhar — cai no <strong>modo demonstração</strong>: campos-base
 * editáveis, marcados com {@code demo = true} para a UI avisar.
 *
 * <p>Campo que o modelo não conseguiu ler volta {@code null} e continua
 * {@code null} aqui: num lançamento de dinheiro, campo vazio que a pessoa
 * preenche é sempre melhor que campo preenchido com palpite.
 *
 * <p>Segurança de upload (§3.2): valida arquivo não-vazio, tamanho máximo,
 * tipo de conteúdo, extensão (allow-list) e assinatura (magic bytes). O arquivo
 * é processado em memória e <strong>nunca persistido</strong> (privacidade).
 */
@Service
public class ExpenseExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseExtractionService.class);

    private static final int MAX_DESCRIPTION = 200;

    private static final Set<String> RECEIPT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif");
    private static final Set<String> RECEIPT_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "heic", "heif");

    private final ExtractionProperties props;
    private final AccountRepository accounts;
    private final ExpenseVisionReader vision;
    private final Clock clock;

    public ExpenseExtractionService(ExtractionProperties props, AccountRepository accounts,
                                    ExpenseVisionReader vision, Clock clock) {
        this.props = props;
        this.accounts = accounts;
        this.vision = vision;
        this.clock = clock;
    }

    /**
     * Extrai (ou sugere) os campos do gasto. Lança
     * {@link com.rastroos.domain.exception.InvalidUploadException}
     * quando o arquivo é inválido — a camada Web mostra a mensagem no formulário.
     */
    public ExtractedExpense extract(UUID userId, MultipartFile file, ExpenseExtractionSource source) {
        validate(file, source);

        ExtractedExpense base = vision.read(userId, file, source)
                .filter(reading -> !reading.isEmpty())
                .map(reading -> fromVision(reading, file, source))
                .orElseGet(() -> stub(file, source));

        UUID accountId = matchAccountByLast4(userId, base.last4());
        if (accountId == null) {
            return base;
        }
        return new ExtractedExpense(base.description(), base.amount(), base.dueDate(), base.fixed(),
                base.categoryId(), base.last4(), accountId, base.demo());
    }

    /**
     * Converte a leitura da IA em sugestão. A data cai para hoje quando o
     * documento não traz uma legível — um vencimento nunca fica em branco no
     * formulário; o valor, ao contrário, fica, porque errar o valor é pior do
     * que pedir para a pessoa digitar.
     */
    private ExtractedExpense fromVision(VisionReading reading, MultipartFile file,
                                        ExpenseExtractionSource source) {
        String description = reading.description() != null
                ? trimDescription(reading.description())
                : fallbackDescription(file, source);
        LocalDate date = reading.date() != null ? reading.date() : LocalDate.now(clock);
        BigDecimal amount = reading.amount();
        return new ExtractedExpense(description, amount, date, false, null,
                reading.last4(), null, false);
    }

    private String fallbackDescription(MultipartFile file, ExpenseExtractionSource source) {
        return source == ExpenseExtractionSource.RECEIPT
                ? "Compra no cartão"
                : descriptionFromFilename(file.getOriginalFilename());
    }

    private static String trimDescription(String raw) {
        String text = raw.trim();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
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
                .filter(a -> a.getKind().isCard() && digits.equals(a.getLast4()))
                .map(Account::getId)
                .findFirst()
                .orElse(null);
    }

    private void validate(MultipartFile file, ExpenseExtractionSource source) {
        boolean receipt = source == ExpenseExtractionSource.RECEIPT;
        UploadGuard.validate(file, props.getMaxFileSizeBytes(),
                receipt ? RECEIPT_TYPES : UploadGuard.DOCUMENT_TYPES,
                receipt ? RECEIPT_EXTENSIONS : UploadGuard.DOCUMENT_EXTENSIONS);
    }

    private static String descriptionFromFilename(String filename) {
        String ext = UploadGuard.extensionOf(filename);
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
