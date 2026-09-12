package com.rastroos.domain.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tudo o que é específico de um fornecedor de IA: os caminhos dos endpoints, o
 * formato do corpo da requisição e onde ler cada campo da resposta.
 *
 * <p>É a costura que mantém o Rastroo$ independente de fornecedor. O resto do
 * código — dossiê de dados, cache de resumos, teto de consumo, índice vetorial —
 * não sabe qual motor está atrás: troca-se a implementação e nada mais muda.
 *
 * <p>Escolha por configuração ({@code ai.provider}). Quem falar o dialeto da
 * OpenAI (Azure OpenAI, Gemini pela camada de compatibilidade, Groq, Together,
 * OpenRouter, Ollama, vLLM…) reaproveita {@link OpenAiProvider} inteiro,
 * mudando só as URLs e os nomes de modelo. Um fornecedor com API própria entra
 * como uma nova implementação desta interface, sem tocar em nada mais.
 */
public interface AiProvider {

    /** Identificador usado em {@code ai.provider}. */
    String id();

    // ── Padrões (usados quando a configuração não diz outra coisa) ───────

    String defaultBaseUrl();

    String defaultChatModel();

    String defaultEmbeddingModel();

    /**
     * Dimensão dos vetores. Precisa casar com {@code vector(N)} da tabela
     * {@code ai_documents} — a validação no boot avisa quando não casa.
     */
    int defaultEmbeddingDimensions();

    // ── Endpoints ────────────────────────────────────────────────────────

    String chatUrl(String baseRoot);

    String embeddingsUrl(String baseRoot);

    // ── Requisições ──────────────────────────────────────────────────────

    Map<String, Object> chatBody(String model, List<Map<String, Object>> messages,
                                 int maxTokens, double temperature,
                                 Map<String, Object> responseFormat);

    Map<String, Object> embeddingBody(String model, List<String> inputs, int dimensions);

    /** Parte de texto de uma mensagem multimodal. */
    Map<String, Object> textPart(String text);

    /** Parte de imagem (foto da notinha). */
    Map<String, Object> imagePart(String dataUrl, String detail);

    /** Parte de documento (PDF de boleto/fatura). */
    Map<String, Object> filePart(String filename, String dataUrl);

    /** Contrato de saída estruturada (JSON Schema estrito). */
    Map<String, Object> jsonSchemaFormat(String name, Map<String, Object> schema);

    // ── Respostas ────────────────────────────────────────────────────────

    /** Texto da resposta, ou {@code null} se não houver. */
    String readContent(JsonNode response);

    /**
     * O que somar ao corpo do chat para receber a resposta em pedaços (SSE).
     * {@code include_usage} é o que mantém a contabilidade de tokens viva no
     * caminho streamado — sem isso, §4.1 ("sempre contabilizar") deixaria de
     * valer justamente na funcionalidade mais usada.
     *
     * <p>Padrão = dialeto OpenAI, que o Gemini também fala pela camada de
     * compatibilidade. Um fornecedor com outro formato sobrescreve.
     */
    default Map<String, Object> streamingOptions() {
        return Map.of("stream", true,
                      "stream_options", Map.of("include_usage", true));
    }

    /**
     * O pedaço de texto de um chunk SSE, ou {@code null} quando o chunk não
     * traz conteúdo (só papéis, sinalizações ou o {@code usage} final).
     */
    default String readStreamDelta(JsonNode chunk) {
        JsonNode choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode content = choices.get(0).path("delta").path("content");
        return content.isTextual() ? content.asText() : null;
    }

    AiTokenUsage readUsage(JsonNode response);

    /** Vetores na ordem das entradas. */
    List<float[]> readEmbeddings(JsonNode response);

    /**
     * Distingue "sem saldo" de "excesso de requisições". Os dois costumam vir
     * como 429, mas um passa sozinho em segundos e o outro só passa quando
     * alguém coloca crédito — repetir o segundo é desperdício puro.
     */
    boolean isOutOfCredit(int status, String responseBody);
}
