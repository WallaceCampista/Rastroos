package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Leitura de boleto/notinha por visão. O que se protege aqui é a diferença
 * entre "não consegui ler" e "chutei": num lançamento de dinheiro, campo vazio
 * que a pessoa preenche é sempre melhor que campo preenchido errado.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseVisionReaderTest {

    @Mock private AiModelClient client;
    @Mock private AiBudgetGuard budget;

    private final UUID alice = UUID.randomUUID();
    private AiProperties props;
    private ExpenseVisionReader reader;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        reader = new ExpenseVisionReader(client, budget,
                new AiCircuitBreakers(CircuitBreakerRegistry.ofDefaults()),
                props, new ObjectMapper());
    }

    @Test
    void visaoDesligada_devolveVazioParaCairNoModoDemonstracao() {
        props.getVision().setEnabled(false);

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)).isEmpty();
        verify(client, never()).chat(any(), any(), any(), anyInt(), anyDouble(), any());
    }

    @Test
    void leituraCompleta_devolveTodosOsCampos() {
        enable("""
                {"description":"Drogaria São Paulo","amount":87.90,
                 "date":"2026-09-10","last4":"1234"}
                """);

        VisionReading reading = reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)
                .orElseThrow();

        assertThat(reading.description()).isEqualTo("Drogaria São Paulo");
        assertThat(reading.amount()).isEqualByComparingTo(new BigDecimal("87.90"));
        assertThat(reading.date()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(reading.last4()).isEqualTo("1234");
        assertThat(reading.isEmpty()).isFalse();
    }

    @Test
    void camposIlegiveis_ficamNulos_nuncaChutados() {
        enable("""
                {"description":"Boleto","amount":null,"date":null,"last4":null}
                """);

        VisionReading reading = reader.read(alice, jpeg(), ExpenseExtractionSource.DOCUMENT)
                .orElseThrow();

        assertThat(reading.description()).isEqualTo("Boleto");
        assertThat(reading.amount()).isNull();
        assertThat(reading.date()).isNull();
        assertThat(reading.last4()).isNull();
    }

    @Test
    void valorNaoPositivo_ehDescartado() {
        enable("""
                {"description":"x","amount":0,"date":null,"last4":null}
                """);

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)
                .orElseThrow().amount()).isNull();
    }

    @Test
    void last4ComTamanhoErrado_ehDescartado() {
        enable("""
                {"description":"x","amount":null,"date":null,"last4":"12"}
                """);

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)
                .orElseThrow().last4()).isNull();
    }

    @Test
    void last4ComRuido_ehLimpoParaOsQuatroDigitos() {
        enable("""
                {"description":"x","amount":null,"date":null,"last4":"**** 4321"}
                """);

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)
                .orElseThrow().last4()).isEqualTo("4321");
    }

    @Test
    void dataEmFormatoInvalido_viraNulo() {
        enable("""
                {"description":"x","amount":null,"date":"10/09/2026","last4":null}
                """);

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)
                .orElseThrow().date()).isNull();
    }

    @Test
    void respostaForaDoContrato_naoQuebraOFluxo() {
        enable("isto não é json");

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)).isEmpty();
    }

    @Test
    void tetoDiarioAtingido_caiNoModoDemonstracaoSemChamar() {
        when(client.isEnabled()).thenReturn(true);
        doThrow(new AiBudgetExceededException("estourou"))
                .when(budget).check(alice, AiFeature.VISION);

        assertThat(reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT)).isEmpty();
        verify(client, never()).chat(any(), any(), any(), anyInt(), anyDouble(), any());
    }

    @Test
    void imagemViajaComoImageUrlComONivelDeDetalheConfigurado() {
        props.getVision().setDetail("high");
        enable("""
                {"description":"x","amount":null,"date":null,"last4":null}
                """);

        reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT);

        Map<String, Object> image = (Map<String, Object>) partOfType("image_url").get("image_url");
        assertThat((String) image.get("url")).startsWith("data:image/jpeg;base64,");
        assertThat(image).containsEntry("detail", "high");
    }

    @Test
    void pdfViajaComoArquivo_naoComoImagem() {
        enable("""
                {"description":"x","amount":null,"date":null,"last4":null}
                """);

        reader.read(alice, new MockMultipartFile("file", "fatura nubank.pdf",
                "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46}),
                ExpenseExtractionSource.DOCUMENT);

        Map<String, Object> file = (Map<String, Object>) partOfType("file").get("file");
        assertThat((String) file.get("filename")).isEqualTo("fatura_nubank.pdf");
        assertThat((String) file.get("file_data")).startsWith("data:application/pdf;base64,");
    }

    @Test
    void pedeSaidaEstruturadaEstrita_paraNaoInterpretarProsa() {
        enable("""
                {"description":"x","amount":null,"date":null,"last4":null}
                """);

        reader.read(alice, jpeg(), ExpenseExtractionSource.RECEIPT);

        ArgumentCaptor<Map<String, Object>> format = ArgumentCaptor.forClass(Map.class);
        verify(client).chat(eq(AiFeature.VISION), eq(alice), any(), anyInt(), anyDouble(),
                format.capture());
        assertThat(format.getValue()).containsEntry("type", "json_schema");
        Map<String, Object> schema = (Map<String, Object>) format.getValue().get("json_schema");
        assertThat(schema).containsEntry("strict", true);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void enable(String jsonAnswer) {
        when(client.isEnabled()).thenReturn(true);
        when(client.provider()).thenReturn(new OpenAiProvider());
        when(client.chat(eq(AiFeature.VISION), eq(alice), any(), anyInt(), anyDouble(), any()))
                .thenReturn(new AiCompletion(jsonAnswer, AiTokenUsage.ZERO));
    }

    /** A parte da mensagem multimodal com o {@code type} pedido. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> partOfType(String type) {
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(client).chat(eq(AiFeature.VISION), eq(alice), messages.capture(),
                anyInt(), anyDouble(), any());
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) messages.getValue().get(0).get("content");
        return content.stream()
                .filter(part -> type.equals(part.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("parte '" + type + "' não encontrada"));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "nota.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }
}
