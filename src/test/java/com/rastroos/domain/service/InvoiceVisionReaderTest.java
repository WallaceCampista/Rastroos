package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.exception.InvoiceReadException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Leitura da fatura inteira. Protege a mesma fronteira da leitura de notinha —
 * não chutar — e o que é específico da fatura: parcela lida certa, marcador de
 * parcela fora da descrição e mensagem acionável quando não dá para ler.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceVisionReaderTest {

    @Mock private AiModelClient client;
    @Mock private AiBudgetGuard budget;

    private final UUID alice = UUID.randomUUID();
    private final Map<String, String> categories = new LinkedHashMap<>();
    private AiProperties props;
    private InvoiceVisionReader reader;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        categories.put("alimentacao", "Alimentação");
        categories.put("transporte", "Transporte");
        categories.put("outros", "Outros");
        reader = new InvoiceVisionReader(client, budget,
                new AiCircuitBreakers(CircuitBreakerRegistry.ofDefaults()), props, new ObjectMapper());
    }

    @Test
    void leituraCompleta_devolveCabecalhoELinhas() {
        enable("""
                {"dueDate":"2026-10-10","total":1234.56,"last4":"**** 1234","items":[
                  {"date":"12/09","description":"LOJA X 03/10","amount":100.00,
                   "installment":3,"installments":10,"type":"PURCHASE","category":"outros"},
                  {"date":"15/09","description":"UBER *TRIP","amount":15.9,
                   "installment":null,"installments":null,"type":"PURCHASE","category":"transporte"},
                  {"date":null,"description":"IOF COMPRA INTERNACIONAL","amount":2.35,
                   "installment":null,"installments":null,"type":"FEE","category":"outros"},
                  {"date":"20/09","description":"PAGAMENTO RECEBIDO","amount":900,
                   "installment":null,"installments":null,"type":"PAYMENT","category":"outros"}
                ]}
                """);

        InvoiceReading reading = reader.read(alice, pdf(), categories);

        assertThat(reading.dueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(reading.total()).isEqualByComparingTo("1234.56");
        assertThat(reading.last4()).isEqualTo("1234");
        assertThat(reading.lines()).hasSize(4);

        InvoiceLine parcel = reading.lines().get(0);
        assertThat(parcel.description()).isEqualTo("LOJA X");
        assertThat(parcel.installment()).isEqualTo((short) 3);
        assertThat(parcel.installments()).isEqualTo((short) 10);
        assertThat(parcel.dateLabel()).isEqualTo("12/09");

        assertThat(reading.lines().get(1).amount()).isEqualByComparingTo(new BigDecimal("15.90"));
        assertThat(reading.lines().get(1).categoryId()).isEqualTo("transporte");
        assertThat(reading.lines().get(2).type()).isEqualTo(InvoiceLineType.FEE);
        assertThat(reading.lines().get(3).type().importable()).isFalse();
    }

    @Test
    void parcelaIncoerente_viraCompraAVista_nuncaProjetaParcelaInventada() {
        enable("""
                {"dueDate":null,"total":null,"last4":null,"items":[
                  {"date":null,"description":"LOJA A","amount":10,"installment":11,"installments":10,
                   "type":"PURCHASE","category":"outros"},
                  {"date":null,"description":"LOJA B","amount":10,"installment":1,"installments":1,
                   "type":"PURCHASE","category":"outros"},
                  {"date":null,"description":"LOJA C","amount":10,"installment":null,"installments":6,
                   "type":"PURCHASE","category":"outros"}
                ]}
                """);

        InvoiceReading reading = reader.read(alice, pdf(), categories);

        assertThat(reading.lines()).extracting(InvoiceLine::installments).containsOnlyNulls();
        assertThat(reading.dueDate()).isNull();
    }

    @Test
    void linhaSemDescricaoOuSemValorPositivo_eDescartada() {
        enable("""
                {"dueDate":null,"total":null,"last4":null,"items":[
                  {"date":null,"description":"","amount":10,"installment":null,"installments":null,
                   "type":"PURCHASE","category":"outros"},
                  {"date":null,"description":"LOJA","amount":0,"installment":null,"installments":null,
                   "type":"PURCHASE","category":"outros"},
                  {"date":null,"description":"LOJA OK","amount":5,"installment":null,"installments":null,
                   "type":"PURCHASE","category":"inexistente"}
                ]}
                """);

        InvoiceReading reading = reader.read(alice, pdf(), categories);

        assertThat(reading.lines()).extracting(InvoiceLine::description).containsExactly("LOJA OK");
        // Categoria fora da lista cai em "outros" em vez de quebrar o lançamento.
        assertThat(reading.lines().get(0).categoryId()).isEqualTo("outros");
    }

    @Test
    void faturaSemLancamentos_avisaEmVezDeAbrirConferenciaVazia() {
        enable("""
                {"dueDate":"2026-10-10","total":0,"last4":null,"items":[]}
                """);

        assertThatThrownBy(() -> reader.read(alice, pdf(), categories))
                .isInstanceOf(InvoiceReadException.class)
                .hasMessage("account.invoice.empty");
    }

    @Test
    void respostaForaDoContrato_avisaLeituraIncompleta() {
        enable("{\"items\":[{\"description\":\"LOJA\"");

        assertThatThrownBy(() -> reader.read(alice, pdf(), categories))
                .hasMessage("account.invoice.unreadable");
    }

    @Test
    void iaDesligada_naoChamaNada() {
        when(client.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> reader.read(alice, pdf(), categories))
                .hasMessage("account.invoice.aiOff");
        verify(client, never()).chat(any(), any(), any(), anyInt(), anyDouble(), any());
    }

    @Test
    void tetoDiarioAtingido_naoChama() {
        when(client.isEnabled()).thenReturn(true);
        doThrow(new AiBudgetExceededException("estourou")).when(budget).check(alice, AiFeature.INVOICE);

        assertThatThrownBy(() -> reader.read(alice, pdf(), categories))
                .hasMessage("account.invoice.budget");
        verify(client, never()).chat(any(), any(), any(), anyInt(), anyDouble(), any());
    }

    @Test
    void provedorFora_avisaIndisponivel() {
        failingProvider();

        assertThatThrownBy(() -> reader.read(alice, pdf(), categories))
                .hasMessage("account.invoice.unavailable");
    }

    @Test
    void pdfComSenha_explicaOQueFazer() {
        failingProvider();
        MockMultipartFile encrypted = new MockMultipartFile("file", "fatura.pdf", "application/pdf",
                "%PDF-1.7\n1 0 obj\ntrailer << /Encrypt 5 0 R >>".getBytes(StandardCharsets.ISO_8859_1));

        assertThatThrownBy(() -> reader.read(alice, encrypted, categories))
                .hasMessage("account.invoice.encrypted");
    }

    @SuppressWarnings("unchecked")
    @Test
    void pedeSaidaEstrita_comAsCategoriasPermitidas_eUsaAFuncionalidadeDeFatura() {
        enable("""
                {"dueDate":null,"total":null,"last4":null,"items":[
                  {"date":null,"description":"LOJA","amount":5,"installment":null,"installments":null,
                   "type":"PURCHASE","category":"outros"}]}
                """);

        reader.read(alice, pdf(), categories);

        ArgumentCaptor<Map<String, Object>> format = ArgumentCaptor.forClass(Map.class);
        verify(client).chat(eq(AiFeature.INVOICE), eq(alice), any(),
                eq(props.getInvoice().getMaxTokens()), eq(0.0), format.capture());
        Map<String, Object> jsonSchema = (Map<String, Object>) format.getValue().get("json_schema");
        assertThat(jsonSchema).containsEntry("strict", true);
        Map<String, Object> schema = (Map<String, Object>) jsonSchema.get("schema");
        Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("items");
        Map<String, Object> item = (Map<String, Object>) items.get("items");
        Map<String, Object> category = (Map<String, Object>) ((Map<String, Object>) item.get("properties")).get("category");
        assertThat((List<String>) category.get("enum")).containsExactly("alimentacao", "transporte", "outros");
        assertThat(item).containsEntry("additionalProperties", false);
    }

    @Test
    void marcadorDeParcela_saiDaDescricaoSoQuandoRepeteAParcelaLida() {
        assertThat(InvoiceVisionReader.stripInstallmentMarker("LOJA X 03/10", 3, 10)).isEqualTo("LOJA X");
        assertThat(InvoiceVisionReader.stripInstallmentMarker("LOJA X PARC 3 DE 10", 3, 10)).isEqualTo("LOJA X");
        assertThat(InvoiceVisionReader.stripInstallmentMarker("LOJA X - 3/10", 3, 10)).isEqualTo("LOJA X");
        assertThat(InvoiceVisionReader.stripInstallmentMarker("LOJA X", 3, 10)).isEqualTo("LOJA X");
        // Número que não é a parcela lida fica: pode ser parte do nome.
        assertThat(InvoiceVisionReader.stripInstallmentMarker("LOJA 24/7", 3, 10)).isEqualTo("LOJA 24/7");
        assertThat(InvoiceVisionReader.stripInstallmentMarker("LOJA 13/10", 3, 10)).isEqualTo("LOJA 13/10");
        assertThat(InvoiceVisionReader.stripInstallmentMarker("03/10", 3, 10)).isEqualTo("03/10");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void enable(String jsonAnswer) {
        when(client.isEnabled()).thenReturn(true);
        when(client.provider()).thenReturn(new OpenAiProvider());
        when(client.chat(eq(AiFeature.INVOICE), eq(alice), any(), anyInt(), anyDouble(), any()))
                .thenReturn(new AiCompletion(jsonAnswer, AiTokenUsage.ZERO));
    }

    private void failingProvider() {
        when(client.isEnabled()).thenReturn(true);
        when(client.provider()).thenReturn(new OpenAiProvider());
        when(client.chat(eq(AiFeature.INVOICE), eq(alice), any(), anyInt(), anyDouble(), any()))
                .thenThrow(new AiUnavailableException("Provedor de IA respondeu 400"));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "fatura.pdf", "application/pdf",
                "%PDF-1.7\nconteudo".getBytes(StandardCharsets.ISO_8859_1));
    }
}
