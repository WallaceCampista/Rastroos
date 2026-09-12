package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A costura que mantém o Rastroo$ independente de fornecedor. O teste fixa o
 * contrato dos dois dialetos suportados — é o que garante que trocar de motor
 * seja configuração, e não refatoração.
 */
class AiProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final OpenAiProvider openai = new OpenAiProvider();
    private final GeminiProvider gemini = new GeminiProvider();

    @Test
    void openai_montaAsUrlsAPartirDaRaiz() {
        assertThat(openai.chatUrl("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(openai.embeddingsUrl("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/embeddings");
    }

    @Test
    void gemini_usaACamadaDeCompatibilidadeEOsProprioModelos() {
        assertThat(gemini.id()).isEqualTo("gemini");
        assertThat(gemini.defaultBaseUrl())
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
        assertThat(gemini.chatUrl(gemini.defaultBaseUrl()))
                .endsWith("/v1beta/openai/chat/completions");
        assertThat(gemini.defaultChatModel()).isNotBlank();
        assertThat(gemini.defaultEmbeddingModel()).isNotBlank();
    }

    @Test
    void dimensaoPadraoDeAmbosCasaComAColunaVetorial() {
        // A coluna ai_documents.embedding é vector(1536): um fornecedor cujo
        // padrão fosse outro quebraria a busca semântica na primeira gravação.
        assertThat(openai.defaultEmbeddingDimensions()).isEqualTo(1536);
        assertThat(gemini.defaultEmbeddingDimensions()).isEqualTo(1536);
    }

    @Test
    void corpoDoChatCarregaModelo_limiteDeTokensETemperatura() {
        Map<String, Object> body = openai.chatBody("m",
                List.of(Map.of("role", "user", "content", "oi")), 120, 0.2, null);

        assertThat(body).containsEntry("model", "m")
                .containsEntry("max_tokens", 120)
                .containsEntry("temperature", 0.2)
                .doesNotContainKey("response_format");
    }

    @Test
    void limiteDeTokensEhSempreEnviado_paraQueUmaRespostaLongaNaoViresurpresa() {
        assertThat(openai.chatBody("m", List.of(), 50, 0.0, null)).containsKey("max_tokens");
    }

    @Test
    void esquemaEstritoEntraQuandoPedido() {
        Map<String, Object> format = openai.jsonSchemaFormat("saida", Map.of("type", "object"));
        Map<String, Object> body = openai.chatBody("m", List.of(), 10, 0.0, format);

        assertThat(body).containsEntry("response_format", format);
        assertThat(format).containsEntry("type", "json_schema");
    }

    @Test
    void embeddingsPedemADimensaoQuandoInformada() {
        assertThat(openai.embeddingBody("m", List.of("a"), 1536))
                .containsEntry("dimensions", 1536);
        assertThat(openai.embeddingBody("m", List.of("a"), 0))
                .doesNotContainKey("dimensions");
    }

    @Test
    void leituraDaRespostaDeChatEDeUso() throws Exception {
        var node = json.readTree("""
                {"choices":[{"message":{"content":"olá"}}],
                 "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                """);

        assertThat(openai.readContent(node)).isEqualTo("olá");
        assertThat(openai.readUsage(node)).isEqualTo(new AiTokenUsage(3, 2, 5));
    }

    @Test
    void usoSemTotal_ehDeduzidoDasPartes() throws Exception {
        var node = json.readTree("{\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":6}}");

        assertThat(openai.readUsage(node).totalTokens()).isEqualTo(10);
    }

    @Test
    void leituraDosVetoresPreservaAOrdem() throws Exception {
        var node = json.readTree("""
                {"data":[{"embedding":[0.1,0.2]},{"embedding":[0.3,0.4]}]}
                """);

        List<float[]> vectors = openai.readEmbeddings(node);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)[0]).isEqualTo(0.1f);
        assertThat(vectors.get(1)[1]).isEqualTo(0.4f);
    }

    @Test
    void semSaldoEhDistinguidoDeExcessoDeRequisicoes() {
        assertThat(openai.isOutOfCredit(429, "{\"error\":{\"type\":\"insufficient_quota\"}}")).isTrue();
        assertThat(openai.isOutOfCredit(429, "{\"error\":{\"type\":\"rate_limit_exceeded\"}}")).isFalse();
        assertThat(openai.isOutOfCredit(500, "insufficient_quota")).isFalse();

        assertThat(gemini.isOutOfCredit(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}")).isTrue();
    }
}
