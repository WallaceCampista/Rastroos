package com.rastroos.domain.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;

/**
 * Lê boleto, fatura ou foto de notinha com o modelo multimodal e devolve os
 * campos do lançamento.
 *
 * <p>Usa <strong>saída estruturada estrita</strong> (JSON Schema com
 * {@code strict: true}): o provedor é obrigado a devolver exatamente os campos
 * declarados, então não há regex adivinhando formato nem resposta em prosa para
 * interpretar. O que o modelo não conseguir ler vem {@code null} — e campo nulo
 * é melhor que campo inventado num lançamento de dinheiro.
 *
 * <p>O resultado continua sendo <em>sugestão editável</em>: quem confirma é a
 * pessoa, no formulário de sempre, com toda a validação normal.
 *
 * <p>O arquivo é lido em memória, enviado como {@code data:} URL e nunca
 * persistido.
 */
@Component
public class ExpenseVisionReader {

    private static final Logger log = LoggerFactory.getLogger(ExpenseVisionReader.class);

    /** Instrução da leitura. Curta de propósito: o esquema já impõe o formato. */
    private static final String DOCUMENT_PROMPT = """
            Leia este documento financeiro (boleto, fatura, comprovante ou recibo) e extraia os campos.
            Regras: valor é o TOTAL a pagar, não parcela nem subtotal; data é a de VENCIMENTO
            (se não houver, a data de emissão); descrição é o nome do beneficiário ou do serviço.
            Se um campo não estiver legível no documento, devolva null — nunca estime.
            """;

    private static final String RECEIPT_PROMPT = """
            Leia esta foto de comprovante de cartão (notinha de maquininha) e extraia os campos.
            Regras: valor é o TOTAL da compra; data é a da transação; descrição é o nome do
            estabelecimento; last4 são os 4 últimos dígitos do cartão, quando impressos.
            Se um campo não estiver legível na imagem, devolva null — nunca estime.
            """;

    private final AiModelClient client;
    private final AiBudgetGuard budget;
    private final AiCircuitBreakers breakers;
    private final AiProperties props;
    private final ObjectMapper json;

    public ExpenseVisionReader(AiModelClient client, AiBudgetGuard budget,
                               AiCircuitBreakers breakers, AiProperties props,
                               ObjectMapper json) {
        this.client = client;
        this.budget = budget;
        this.breakers = breakers;
        this.props = props;
        this.json = json;
    }

    public boolean isEnabled() {
        return props.getVision().isEnabled() && client.isEnabled();
    }

    /**
     * Campos lidos do arquivo, ou {@link Optional#empty()} quando a visão está
     * desligada, sem orçamento ou o provedor falhou — nesses casos o chamador
     * cai no modo demonstração.
     */
    public Optional<VisionReading> read(UUID userId, MultipartFile file,
                                       ExpenseExtractionSource source) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            budget.check(userId, AiFeature.VISION);
            return breakers.call(AiCircuitBreakers.VISION, () -> readRemote(userId, file, source));
        } catch (RuntimeException e) {
            log.warn("Leitura por visão indisponível ({}); caindo no modo demonstração",
                    e.toString());
            return Optional.empty();
        }
    }

    private Optional<VisionReading> readRemote(UUID userId, MultipartFile file,
                                               ExpenseExtractionSource source) {
        String instruction = source == ExpenseExtractionSource.RECEIPT
                ? RECEIPT_PROMPT : DOCUMENT_PROMPT;

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(
                client.provider().textPart(instruction), filePart(file)));

        AiCompletion completion = client.chat(AiFeature.VISION, userId, List.of(userMessage),
                props.getVision().getMaxTokens(), 0.0, responseSchema());
        return parse(completion.content());
    }

    // ── Contrato de saída ────────────────────────────────────────────────

    /**
     * JSON Schema estrito. Todos os campos são obrigatórios e anuláveis: é
     * assim que o modo estrito do provedor permite "não consegui ler" sem abrir
     * espaço para o modelo omitir ou inventar chave.
     */
    private Map<String, Object> responseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("description", nullableString("Nome do beneficiário, serviço ou estabelecimento"));
        properties.put("amount", nullable("number", "Valor total, em reais, com centavos"));
        properties.put("date", nullableString("Data no formato AAAA-MM-DD"));
        properties.put("last4", nullableString("4 últimos dígitos do cartão, só dígitos"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("description", "amount", "date", "last4"));
        schema.put("additionalProperties", false);

        return client.provider().jsonSchemaFormat("expense_extraction", schema);
    }

    private static Map<String, Object> nullableString(String description) {
        return nullable("string", description);
    }

    private static Map<String, Object> nullable(String type, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", List.of(type, "null"));
        node.put("description", description);
        return node;
    }

    // ── Conversão ────────────────────────────────────────────────────────

    private Optional<VisionReading> parse(String content) {
        try {
            JsonNode node = json.readTree(content);
            return Optional.of(new VisionReading(
                    text(node, "description"),
                    amount(node),
                    date(node),
                    digits(node)));
        } catch (Exception e) {
            // Esquema estrito torna isso raro; se acontecer, vale o stub.
            log.warn("Resposta da visão em formato inesperado: {}", e.toString());
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String raw = value.asText().trim();
        return raw.isEmpty() ? null : raw;
    }

    /** Valor só é aceito se positivo: zero ou negativo em nota é erro de leitura. */
    private static BigDecimal amount(JsonNode node) {
        JsonNode value = node.get("amount");
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        BigDecimal amount = value.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
        return amount.signum() > 0 ? amount : null;
    }

    private static LocalDate date(JsonNode node) {
        String raw = text(node, "date");
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Só aceita exatamente 4 dígitos — o casamento com o cartão depende disso. */
    private static String digits(JsonNode node) {
        String raw = text(node, "last4");
        if (raw == null) {
            return null;
        }
        String onlyDigits = raw.replaceAll("\\D", "");
        return onlyDigits.length() == 4 ? onlyDigits : null;
    }

    /**
     * Monta a parte do arquivo. PDF e imagem entram por caminhos diferentes na
     * API: imagem vai como {@code image_url} (com o nível de detalhe, que é o
     * que decide se a letra miúda da notinha é legível) e PDF vai como
     * {@code file}, porque um PDF embutido numa URL de imagem é recusado.
     */
    private Map<String, Object> filePart(MultipartFile file) {
        String contentType = normalizedType(file);
        String dataUrl = "data:" + contentType + ";base64," + base64(file);

        if ("application/pdf".equals(contentType)) {
            return client.provider().filePart(safeName(file.getOriginalFilename()), dataUrl);
        }
        return client.provider().imagePart(dataUrl, props.getVision().getDetail());
    }

    private static String normalizedType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase();
    }

    private static String base64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new AiUnavailableException("Não consegui ler o arquivo enviado", e);
        }
    }

    /** Nome enviado ao provedor: sem caminho e sem caractere exótico. */
    private static String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "documento.pdf";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "documento.pdf" : cleaned;
    }
}
