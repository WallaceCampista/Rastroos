package com.rastroos.domain.service;

/**
 * Google Gemini pela <strong>camada de compatibilidade OpenAI</strong>
 * ({@code /v1beta/openai}): o corpo e a resposta são os mesmos do
 * {@link OpenAiProvider}, então só mudam a URL e os nomes dos modelos.
 *
 * <p>Para usar, basta configuração — nenhuma alteração de código:
 * <pre>
 * AI_PROVIDER=gemini
 * AI_API_KEY=&lt;chave do Google AI Studio&gt;
 * </pre>
 *
 * <p><strong>Atenção à dimensão dos vetores.</strong> A coluna
 * {@code ai_documents.embedding} é {@code vector(1536)}. O padrão aqui é
 * {@code gemini-embedding-001} pedindo 1536 dimensões justamente para caber
 * sem migração. Trocar para um modelo de outra dimensão (por exemplo
 * {@code text-embedding-004}, de 768) exige um changeset alterando a coluna e
 * reindexar — a validação no boot avisa antes de gravar qualquer coisa errada.
 *
 * <p>Nomes de modelo mudam com frequência no catálogo do fornecedor; os padrões
 * abaixo são ponto de partida e podem ser sobrescritos por {@code AI_MODEL} e
 * {@code AI_EMBEDDING_MODEL}.
 */
public class GeminiProvider extends OpenAiProvider {

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/openai";
    }

    @Override
    public String defaultChatModel() {
        return "gemini-2.0-flash-lite";
    }

    @Override
    public String defaultEmbeddingModel() {
        return "gemini-embedding-001";
    }

    @Override
    public int defaultEmbeddingDimensions() {
        return 1536;
    }

    /** O Google sinaliza cota esgotada com {@code RESOURCE_EXHAUSTED}. */
    @Override
    public boolean isOutOfCredit(int status, String responseBody) {
        return status == 429 && responseBody != null
                && (responseBody.contains("RESOURCE_EXHAUSTED")
                    || responseBody.contains("insufficient_quota"));
    }
}
