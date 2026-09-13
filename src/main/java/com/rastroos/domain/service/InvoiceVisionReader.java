package com.rastroos.domain.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.exception.InvoiceReadException;

/**
 * Lê uma fatura de cartão inteira com o modelo multimodal: vencimento, total,
 * 4 últimos dígitos e cada linha de lançamento, com parcela quando houver.
 *
 * <p>Mesmo contrato do {@link ExpenseVisionReader}: <strong>saída estruturada
 * estrita</strong> (JSON Schema) e campo ilegível volta {@code null}. O
 * resultado é sempre conferido pela pessoa antes de virar lançamento.
 *
 * <p>A chamada HTTP acontece fora de qualquer transação (§4.1) e o arquivo é
 * lido em memória, nunca persistido.
 */
@Component
public class InvoiceVisionReader {

    private static final Logger log = LoggerFactory.getLogger(InvoiceVisionReader.class);

    private static final int MAX_DESCRIPTION = 200;
    private static final int MAX_DATE_LABEL = 10;
    private static final int MAX_INSTALLMENTS = 99;
    private static final String FALLBACK_CATEGORY = "outros";

    private static final String PROMPT = """
            Leia esta fatura de cartão de crédito e extraia os lançamentos.
            Regras:
            - items: uma entrada por linha de lançamento (compras, parcelas, assinaturas, encargos,
              estornos e pagamentos). Nunca inclua totais, subtotais, limite, saldo anterior ou resumos.
            - description: o estabelecimento como impresso, SEM o marcador de parcela
              (ex.: "LOJA X 03/10" vira "LOJA X").
            - amount: valor da linha em reais, sempre positivo, inclusive estorno e pagamento.
              Em compra internacional, o valor já convertido em reais.
            - installment e installments: só quando a linha indica parcela ("03/10" = 3 e 10;
              "PARC 3 DE 10" = 3 e 10). Sem indicação de parcela, null nos dois.
            - type: PURCHASE para compra, parcela ou assinatura; FEE para IOF, anuidade, juros,
              multa ou tarifa; CREDIT para estorno, crédito ou devolução; PAYMENT para pagamento
              da fatura anterior.
            - date: a data da linha exatamente como impressa (ex.: "12/09"), ou null.
            - category: a categoria mais provável do gasto, entre as permitidas.
            - dueDate: o vencimento desta fatura em AAAA-MM-DD; se o ano não estiver impresso, null.
            - total: o total a pagar desta fatura.
            - last4: os 4 últimos dígitos do cartão, se impressos.
            Se um campo não estiver legível, devolva null — nunca estime. Não invente lançamentos.
            """;

    private final AiModelClient client;
    private final AiBudgetGuard budget;
    private final AiCircuitBreakers breakers;
    private final AiProperties props;
    private final ObjectMapper json;

    public InvoiceVisionReader(AiModelClient client, AiBudgetGuard budget,
                               AiCircuitBreakers breakers, AiProperties props,
                               ObjectMapper json) {
        this.client = client;
        this.budget = budget;
        this.breakers = breakers;
        this.props = props;
        this.json = json;
    }

    public boolean isEnabled() {
        return props.getInvoice().isEnabled() && client.isEnabled();
    }

    /**
     * Lê a fatura. Qualquer impedimento sai como {@link InvoiceReadException}
     * com a chave da mensagem que a pessoa vai ver.
     *
     * @param categories categorias permitidas, id → nome (a ordem é a da tela)
     */
    public InvoiceReading read(UUID userId, MultipartFile file, Map<String, String> categories) {
        if (!isEnabled()) {
            throw new InvoiceReadException("account.invoice.aiOff");
        }
        try {
            budget.check(userId, AiFeature.INVOICE);
        } catch (AiBudgetExceededException e) {
            throw new InvoiceReadException("account.invoice.budget");
        }

        String content;
        try {
            content = breakers.call(AiCircuitBreakers.INVOICE, () -> readRemote(userId, file, categories));
        } catch (RuntimeException e) {
            log.warn("Leitura de fatura indisponível: {}", e.toString());
            // PDF com senha costuma ser recusado pelo provedor; a mensagem genérica
            // não diria à pessoa o que fazer.
            throw new InvoiceReadException(isEncryptedPdf(file)
                    ? "account.invoice.encrypted"
                    : "account.invoice.unavailable");
        }

        InvoiceReading reading = parse(content, categories);
        if (reading.lines().isEmpty()) {
            throw new InvoiceReadException("account.invoice.empty");
        }
        return reading;
    }

    private String readRemote(UUID userId, MultipartFile file, Map<String, String> categories) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(client.provider().textPart(PROMPT), filePart(file)));

        return client.chat(AiFeature.INVOICE, userId, List.of(userMessage),
                props.getInvoice().getMaxTokens(), 0.0, responseSchema(categories)).content();
    }

    // ── Contrato de saída ────────────────────────────────────────────────

    /** JSON Schema estrito: todo campo obrigatório e anulável, nenhum campo extra. */
    Map<String, Object> responseSchema(Map<String, String> categories) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("enum", List.copyOf(categories.keySet()));
        category.put("description", "Categoria: " + describe(categories));

        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", "string");
        type.put("enum", List.of("PURCHASE", "FEE", "CREDIT", "PAYMENT"));

        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("date", nullable("string", "Data da linha como impressa"));
        itemProps.put("description", described("string", "Estabelecimento, sem o marcador de parcela"));
        itemProps.put("amount", described("number", "Valor da linha em reais, positivo"));
        itemProps.put("installment", nullable("integer", "Número da parcela desta fatura"));
        itemProps.put("installments", nullable("integer", "Total de parcelas da compra"));
        itemProps.put("type", type);
        itemProps.put("category", category);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", itemProps);
        item.put("required", List.copyOf(itemProps.keySet()));
        item.put("additionalProperties", false);

        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "array");
        items.put("items", item);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dueDate", nullable("string", "Vencimento da fatura, AAAA-MM-DD"));
        properties.put("total", nullable("number", "Total a pagar da fatura, em reais"));
        properties.put("last4", nullable("string", "4 últimos dígitos do cartão"));
        properties.put("items", items);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);

        return client.provider().jsonSchemaFormat("invoice_extraction", schema);
    }

    private static String describe(Map<String, String> categories) {
        StringBuilder sb = new StringBuilder();
        categories.forEach((id, name) -> {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(id).append(" = ").append(name);
        });
        return sb.toString();
    }

    private static Map<String, Object> described(String type, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.put("description", description);
        return node;
    }

    private static Map<String, Object> nullable(String type, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", List.of(type, "null"));
        node.put("description", description);
        return node;
    }

    // ── Conversão ────────────────────────────────────────────────────────

    InvoiceReading parse(String content, Map<String, String> categories) {
        JsonNode root;
        try {
            root = json.readTree(content);
        } catch (IOException e) {
            log.warn("Resposta da leitura de fatura fora do formato: {}", e.toString());
            throw new InvoiceReadException("account.invoice.unreadable");
        }
        if (root == null || !root.isObject()) {
            throw new InvoiceReadException("account.invoice.unreadable");
        }

        List<InvoiceLine> lines = new ArrayList<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            int max = props.getInvoice().getMaxItems();
            for (JsonNode item : items) {
                if (lines.size() >= max) {
                    log.warn("Fatura com mais de {} linhas: o excedente foi descartado", max);
                    break;
                }
                InvoiceLine line = line(item, categories);
                if (line != null) {
                    lines.add(line);
                }
            }
        }
        return new InvoiceReading(date(root, "dueDate"), positive(root.get("total")),
                last4(root), List.copyOf(lines));
    }

    /** Linha sem descrição ou sem valor positivo não vira item: não há o que lançar. */
    private InvoiceLine line(JsonNode item, Map<String, String> categories) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String description = text(item, "description");
        BigDecimal amount = positive(item.get("amount"));
        if (description == null || amount == null) {
            return null;
        }
        InvoiceLineType type = InvoiceLineType.parse(text(item, "type"));

        Short installment = null;
        Short installments = null;
        Integer current = integer(item, "installment");
        Integer total = integer(item, "installments");
        if (type.importable() && current != null && total != null
                && total >= 2 && total <= MAX_INSTALLMENTS && current >= 1 && current <= total) {
            installment = current.shortValue();
            installments = total.shortValue();
            description = stripInstallmentMarker(description, current, total);
        }

        String dateLabel = text(item, "date");
        if (dateLabel != null && dateLabel.length() > MAX_DATE_LABEL) {
            dateLabel = null;
        }

        String category = text(item, "category");
        if (category == null || !categories.containsKey(category)) {
            category = categories.containsKey(FALLBACK_CATEGORY) || categories.isEmpty()
                    ? FALLBACK_CATEGORY
                    : categories.keySet().iterator().next();
        }

        return new InvoiceLine(dateLabel, truncate(description), amount,
                installment, installments, type, category);
    }

    /**
     * Tira o "03/10" (ou "PARC 3 DE 10") do fim da descrição quando ele repete
     * a parcela já lida. É o que mantém a descrição igual de uma fatura para a
     * outra — e o cruzamento com as parcelas já lançadas depende disso.
     */
    static String stripInstallmentMarker(String description, int current, int total) {
        Pattern marker = Pattern.compile(
                "(?i)[\\s\\-]*(?:PARC(?:ELA)?\\.?\\s*)?(?<!\\d)0*" + current
                        + "\\s*(?:/|DE)\\s*0*" + total + "\\s*$");
        Matcher m = marker.matcher(description);
        if (!m.find()) {
            return description;
        }
        String stripped = description.substring(0, m.start()).trim();
        return stripped.isEmpty() ? description : stripped;
    }

    private static String truncate(String text) {
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION).trim() : text;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String raw = value.asText().trim();
        return raw.isEmpty() ? null : raw;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.canConvertToExactIntegral() ? value.intValue() : null;
    }

    /** Valor só é aceito se positivo: zero ou negativo é erro de leitura. */
    private static BigDecimal positive(JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        BigDecimal amount = value.decimalValue().setScale(2, RoundingMode.HALF_UP);
        return amount.signum() > 0 ? amount : null;
    }

    private static LocalDate date(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String last4(JsonNode node) {
        String raw = text(node, "last4");
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() == 4 ? digits : null;
    }

    private Map<String, Object> filePart(MultipartFile file) {
        String contentType = UploadGuard.normalizeContentType(file.getContentType());
        String dataUrl = "data:" + contentType + ";base64," + base64(file);
        if ("application/pdf".equals(contentType)) {
            return client.provider().filePart("fatura.pdf", dataUrl);
        }
        return client.provider().imagePart(dataUrl, props.getVision().getDetail());
    }

    private static String base64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new AiUnavailableException("Não consegui ler o arquivo enviado", e);
        }
    }

    /** PDF criptografado declara {@code /Encrypt} no trailer. */
    static boolean isEncryptedPdf(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
                return false;
            }
            return new String(bytes, StandardCharsets.ISO_8859_1).contains("/Encrypt");
        } catch (IOException e) {
            return false;
        }
    }
}
