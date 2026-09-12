package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;

/**
 * Cliente HTTP da IA contra um servidor simulado: o que é repetido, o que não
 * é, e se todo token consumido chega ao livro-caixa.
 */
@ExtendWith(MockitoExtension.class)
class AiModelClientTest {

    @Mock private AiUsageRecorder usage;

    private final UUID alice = UUID.randomUUID();
    private AiProperties props;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setApiKey("chave-de-teste");
        props.setBaseUrl("https://fornecedor.local/v1");
        props.setMaxRetries(2);
    }

    @Test
    void semChave_ficaDesligadoELancaAoSerUsado() {
        props.setApiKey("");
        AiModelClient client = new AiModelClient(props, new OpenAiProvider(), usage);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.embed(alice, List.of("x")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void respostaOk_devolveOTextoERegistraOsTokens() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.requestTo(
                        "https://fornecedor.local/v1/chat/completions"))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer chave-de-teste"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.max_tokens").value(50))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"choices":[{"message":{"content":"  Olá  "}}],
                         "usage":{"prompt_tokens":11,"completion_tokens":4,"total_tokens":15}}
                        """, MediaType.APPLICATION_JSON));

        AiCompletion completion = h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, null);

        assertThat(completion.content()).isEqualTo("Olá");
        assertThat(completion.usage().totalTokens()).isEqualTo(15);
        verify(usage).record(alice, AiFeature.CHAT, "gpt-4o-mini",
                new AiTokenUsage(11, 4, 15));
        h.server.verify();
    }

    @Test
    void respostaVazia_naoPassaComoSucesso() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void erro5xx_ehRepetidoEPodeDarCertoNaSegunda() {
        Harness h = harness();
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withServerError());
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"choices":[{"message":{"content":"deu certo"}}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """, MediaType.APPLICATION_JSON));

        AiCompletion completion = h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, null);

        assertThat(completion.content()).isEqualTo("deu certo");
        h.server.verify();
    }

    @Test
    void erro400_naoEhRepetido() {
        Harness h = harness();
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withBadRequest());

        assertThatThrownBy(() -> h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, null))
                .isInstanceOf(AiUnavailableException.class);

        h.server.verify();   // exatamente uma tentativa
    }

    @Test
    void contaSemCredito_falhaNaHoraSemRepetir() {
        Harness h = harness();
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators
                        .withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"insufficient_quota\"}}"));

        assertThatThrownBy(() -> h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("sem crédito");

        h.server.verify();   // sem retry: repetir não traz crédito de volta
        verify(usage, never()).record(any(), any(), any(), any());
    }

    @Test
    void excessoDeRequisicoes_ehRepetido() {
        Harness h = harness();
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add("Retry-After", "0");
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators
                        .withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(retryAfter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"rate_limit_exceeded\"}}"));
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"choices":[{"message":{"content":"ok"}}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, null).content())
                .isEqualTo("ok");
        h.server.verify();
    }

    @Test
    void embeddings_devolvemVetoresNaOrdemERegistramTokens() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.requestTo("https://fornecedor.local/v1/embeddings"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.dimensions").value(1536))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"data":[{"embedding":[%s]},{"embedding":[%s]}],
                         "usage":{"prompt_tokens":7,"completion_tokens":0,"total_tokens":7}}
                        """.formatted(vector(1536, "0.1"), vector(1536, "0.2")),
                        MediaType.APPLICATION_JSON));

        AiEmbeddings embeddings = h.client.embed(alice, List.of("a", "b"));

        assertThat(embeddings.vectors()).hasSize(2);
        assertThat(embeddings.vectors().get(0)[0]).isEqualTo(0.1f);
        verify(usage).record(alice, AiFeature.EMBEDDING, "text-embedding-3-small",
                new AiTokenUsage(7, 0, 7));
    }

    @Test
    void vetorComDimensaoErrada_ehRecusadoAntesDeChegarAoBanco() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"data":[{"embedding":[0.1,0.2,0.3]}],
                         "usage":{"total_tokens":1}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.client.embed(alice, List.of("a")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("dimensões");
    }

    @Test
    void quantidadeDeVetoresDiferenteDaEntrada_ehRecusada() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"data\":[],\"usage\":{\"total_tokens\":1}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.client.embed(alice, List.of("a", "b")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("vetores");
    }

    @Test
    void listaVaziaNaoChamaOFornecedor() {
        Harness h = harness();

        assertThat(h.client.embed(alice, List.of()).vectors()).isEmpty();

        h.server.verify();   // nenhuma requisição esperada, nenhuma feita
        verify(usage, never()).record(any(), any(), any(), any());
    }

    @Test
    void fornecedorGemini_usaAsUrlsEModelosDele() {
        props.setBaseUrl("");
        props.setModel("");
        props.getEmbedding().setModel("");
        AiModelClient client = new AiModelClient(props, new GeminiProvider(), usage);

        assertThat(client.chatModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(client.embeddingModel()).isEqualTo("gemini-embedding-001");
        assertThat(client.embeddingDimensions()).isEqualTo(1536);
    }

    @Test
    void baseUrlLegadaApontandoParaChatCompletions_ehNormalizada() {
        props.setBaseUrl("https://fornecedor.local/v1/chat/completions");
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.requestTo(
                        "https://fornecedor.local/v1/chat/completions"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"choices":[{"message":{"content":"ok"}}],"usage":{"total_tokens":1}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(h.client.chat(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 10, 0.0, null).content())
                .isEqualTo("ok");
        h.server.verify();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    // ── Streaming ────────────────────────────────────────────

    @Test
    void streaming_entregaOsPedacosEmOrdemEDevolveOTextoInteiro() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.requestTo(
                        "https://fornecedor.local/v1/chat/completions"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.stream").value(true))
                .andExpect(MockRestRequestMatchers.jsonPath("$.stream_options.include_usage").value(true))
                .andRespond(MockRestResponseCreators.withSuccess(SSE_OK, MediaType.TEXT_EVENT_STREAM));

        List<String> deltas = new java.util.ArrayList<>();
        AiCompletion completion = h.client.chatStream(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, deltas::add);

        assertThat(deltas).containsExactly("Olá", ", tudo bem?");
        assertThat(completion.content()).isEqualTo("Olá, tudo bem?");
        // O usage vem acumulado nos chunks: vale o último.
        assertThat(completion.usage().totalTokens()).isEqualTo(42);
        verify(usage).record(eq(alice), eq(AiFeature.CHAT), eq("gpt-4o-mini"),
                eq(new AiTokenUsage(17, 25, 42)));
    }

    @Test
    void streaming_chunkQuebradoNaoDerrubaOFluxo() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess(SSE_MALFORMADO,
                        MediaType.TEXT_EVENT_STREAM));

        AiCompletion completion = h.client.chatStream(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, delta -> { });

        assertThat(completion.content()).isEqualTo("vale isto");
    }

    /** Streaming não repete: meia resposta na tela + retry = texto duplicado. */
    @Test
    void streaming_erroDoProvedorNaoERepetido() {
        Harness h = harness();
        h.server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withServerError());

        assertThatThrownBy(() -> h.client.chatStream(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, delta -> { }))
                .isInstanceOf(AiUnavailableException.class);

        h.server.verify();
        verify(usage, never()).record(any(), any(), any(), any());
    }

    @Test
    void streaming_semConteudoLancaIndisponivel() {
        Harness h = harness();
        h.server.expect(MockRestRequestMatchers.anything())
                .andRespond(MockRestResponseCreators.withSuccess("data: [DONE]\n\n",
                        MediaType.TEXT_EVENT_STREAM));

        assertThatThrownBy(() -> h.client.chatStream(AiFeature.CHAT, alice,
                List.of(Map.of("role", "user", "content", "oi")), 50, 0.2, delta -> { }))
                .isInstanceOf(AiUnavailableException.class);
    }

    private static final String SSE_OK =
            "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"Olá\"}}],"
            + "\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":9,\"total_tokens\":26}}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\", tudo bem?\"}}],"
            + "\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":25,\"total_tokens\":42}}\n\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n\n";

    private static final String SSE_MALFORMADO =
            "data: {\"choices\":[{\"delta\":{\"content\":\"vale\"}}]}\n\n"
            + "data: {isso nao e json}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\" isto\"}}],"
            + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}\n\n"
            + "data: [DONE]\n\n";

    private record Harness(AiModelClient client, MockRestServiceServer server) { }

    /** Cliente apontado para um servidor simulado, pelo construtor de teste. */
    private Harness harness() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient stubbed = builder.build();
        AiModelClient client =
                new AiModelClient(props, new OpenAiProvider(), usage, stubbed, stubbed);
        return new Harness(client, server);
    }

    /** Vetor JSON com {@code n} posições, para casar com a dimensão esperada. */
    private static String vector(int n, String value) {
        return String.join(",", java.util.Collections.nCopies(n, value));
    }
}
