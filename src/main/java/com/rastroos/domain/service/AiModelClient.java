package com.rastroos.domain.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;

/**
 * Cliente HTTP único do provedor de IA (API compatível com OpenAI).
 *
 * <p>Responsabilidades, todas de infraestrutura: aplicar timeout, repetir o que
 * vale a pena repetir (429 e 5xx, respeitando {@code Retry-After}) e
 * <strong>contabilizar os tokens de toda chamada</strong> no livro-caixa.
 * Nenhuma regra de negócio mora aqui — quem decide o que perguntar são os
 * services do Alfredo.
 *
 * <p>O <em>formato</em> da conversa com o fornecedor não mora aqui tampouco:
 * fica no {@link AiProvider} injetado. Trocar OpenAI por Gemini (ou qualquer
 * outro) é escolher outra implementação por configuração — esta classe não
 * muda.
 *
 * <p>Segurança: a chave vai só no cabeçalho {@code Authorization} e nunca é
 * logada; prompts e respostas também não (§3.2) — o log registra apenas
 * funcionalidade, status e tentativa.
 *
 * <p>Com a integração desligada ({@link AiProperties#isEnabled()} falso) nenhum
 * {@code RestClient} é construído e as chamadas lançam
 * {@link AiUnavailableException}, que os chamadores traduzem em modo
 * demonstração.
 */
@Component
public class AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClient.class);

    /** Teto do {@code Retry-After}: além disso não vale segurar a requisição. */
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5);

    /** Base do backoff exponencial entre tentativas. */
    private static final long BACKOFF_BASE_MS = 400;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiProperties props;
    private final AiProvider provider;
    private final AiUsageRecorder usage;
    private final RestClient textClient;
    private final RestClient visionClient;

    /** Valores efetivos (configuração, ou o padrão do fornecedor). */
    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final int embeddingDimensions;

    @Autowired
    public AiModelClient(AiProperties props, AiProvider provider, AiUsageRecorder usage) {
        this(props, provider, usage, null, null);
    }

    /**
     * Construtor com os clientes HTTP já prontos — usado pelos testes, que
     * apontam para um servidor simulado. Nulos fazem cair no comportamento
     * normal: construir a partir das propriedades, e só quando há credencial.
     */
    AiModelClient(AiProperties props, AiProvider provider, AiUsageRecorder usage,
                  RestClient textClient, RestClient visionClient) {
        this.props = props;
        this.provider = provider;
        this.usage = usage;
        this.baseUrl = props.resolvedBaseUrl(provider.defaultBaseUrl());
        this.chatModel = props.resolvedModel(provider.defaultChatModel());
        this.embeddingModel = props.getEmbedding().getModel() == null
                || props.getEmbedding().getModel().isBlank()
                ? provider.defaultEmbeddingModel()
                : props.getEmbedding().getModel().trim();
        this.embeddingDimensions = props.getEmbedding().getDimensions() > 0
                ? props.getEmbedding().getDimensions()
                : provider.defaultEmbeddingDimensions();
        this.textClient = textClient != null ? textClient
                : props.isEnabled() ? build(props, props.getReadTimeoutMs()) : null;
        this.visionClient = visionClient != null ? visionClient
                : props.isEnabled() ? build(props, props.getVision().getReadTimeoutMs()) : null;
        if (props.isEnabled()) {
            log.info("IA habilitada: fornecedor={} modelo={} embeddings={} ({} dimensões)",
                    provider.id(), chatModel, embeddingModel, embeddingDimensions);
        } else {
            log.info("IA em modo demonstração (ai.api-key ausente)");
        }
    }

    /** Modelo de texto em uso — rótulo do livro-caixa e da chave do cache. */
    public String chatModel() {
        return chatModel;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public int embeddingDimensions() {
        return embeddingDimensions;
    }

    /** Fornecedor ativo, para quem precisa montar partes de mensagem. */
    public AiProvider provider() {
        return provider;
    }

    private static RestClient build(AiProperties p, int readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(p.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    public boolean isEnabled() {
        return textClient != null;
    }

    /**
     * Chat completions. {@code responseFormat} nulo = texto livre; preenchido
     * com um JSON Schema estrito, o provedor devolve JSON válido por contrato
     * (usado pela leitura de documentos).
     */
    public AiCompletion chat(AiFeature feature, UUID userId, List<Map<String, Object>> messages,
                             int maxTokens, double temperature, Map<String, Object> responseFormat) {
        requireEnabled();

        Map<String, Object> body = provider.chatBody(
                chatModel, messages, maxTokens, temperature, responseFormat);

        RestClient client = feature == AiFeature.VISION ? visionClient : textClient;
        JsonNode response = post(client, provider.chatUrl(baseUrl), body, feature);

        AiTokenUsage tokens = provider.readUsage(response);
        usage.record(userId, feature, chatModel, tokens);

        String content = provider.readContent(response);
        if (content == null || content.isBlank()) {
            throw new AiUnavailableException("Resposta vazia do provedor de IA");
        }
        return new AiCompletion(content.trim(), tokens);
    }

    /**
     * Chat em <strong>streaming</strong>: o texto chega em pedaços e cada um é
     * entregue a {@code onDelta} assim que sai do fio. Devolve o texto inteiro
     * e os tokens no fim, para o chamador persistir e contabilizar como em
     * qualquer outra chamada.
     *
     * <p><strong>Sem retry, de propósito.</strong> Repetir uma chamada que já
     * escreveu meia resposta na tela duplicaria o texto para o usuário. Falhou
     * no meio, quem chama decide (e o evento final do SSE carrega o texto
     * autoritativo, então a tela nunca fica com um pedaço órfão).
     */
    public AiCompletion chatStream(AiFeature feature, UUID userId,
                                   List<Map<String, Object>> messages,
                                   int maxTokens, double temperature,
                                   Consumer<String> onDelta) {
        requireEnabled();

        Map<String, Object> body = new LinkedHashMap<>(
                provider.chatBody(chatModel, messages, maxTokens, temperature, null));
        body.putAll(provider.streamingOptions());

        StringBuilder full = new StringBuilder();
        AiTokenUsage[] tokens = { AiTokenUsage.ZERO };

        try {
            textClient.post()
                    .uri(provider.chatUrl(baseUrl))
                    .headers(h -> {
                        h.setBearerAuth(props.getApiKey());
                        h.setContentType(MediaType.APPLICATION_JSON);
                        h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                    })
                    .body(body)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw streamFailure(feature, status, response);
                        }
                        readEventStream(response.getBody(), full, tokens, onDelta);
                        return null;
                    });
        } catch (AiUnavailableException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("IA {}: falha de rede durante o streaming", feature);
            throw new AiUnavailableException("Provedor de IA inacessível", e);
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Falha lendo o streaming do provedor de IA", e);
        }

        usage.record(userId, feature, chatModel, tokens[0]);

        String content = full.toString().trim();
        if (content.isEmpty()) {
            throw new AiUnavailableException("Resposta vazia do provedor de IA");
        }
        return new AiCompletion(content, tokens[0]);
    }

    /**
     * Lê o corpo {@code text/event-stream} linha a linha. Só interessam as
     * linhas {@code data:}; {@code [DONE]} encerra. O {@code usage} vem
     * acumulado nos chunks — guardamos o último não-zero.
     */
    private void readEventStream(java.io.InputStream in, StringBuilder full,
                                 AiTokenUsage[] tokens, Consumer<String> onDelta)
            throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                JsonNode chunk;
                try {
                    chunk = MAPPER.readTree(payload);
                } catch (IOException malformed) {
                    // Um chunk quebrado não pode derrubar a resposta inteira.
                    continue;
                }
                String delta = provider.readStreamDelta(chunk);
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    onDelta.accept(delta);
                }
                AiTokenUsage chunkUsage = provider.readUsage(chunk);
                if (chunkUsage != null && chunkUsage.totalTokens() > 0) {
                    tokens[0] = chunkUsage;
                }
            }
        }
    }

    /** Erro HTTP no início do streaming, com a mesma leitura de "sem crédito". */
    private AiUnavailableException streamFailure(AiFeature feature, HttpStatusCode status,
                                                 org.springframework.http.client.ClientHttpResponse response) {
        String bodyText = "";
        try {
            bodyText = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Sem corpo legível: o status já basta para a mensagem.
        }
        if (provider.isOutOfCredit(status.value(), bodyText)) {
            log.error("IA {}: conta do provedor sem crédito", feature);
            return new AiUnavailableException("Conta do provedor de IA sem crédito");
        }
        log.warn("IA {}: provedor respondeu {} ao abrir o streaming", feature, status.value());
        return new AiUnavailableException("Provedor de IA respondeu " + status.value());
    }

    /** Vetoriza um lote de textos; a ordem da saída espelha a da entrada. */
    public AiEmbeddings embed(UUID userId, List<String> inputs) {
        requireEnabled();
        if (inputs.isEmpty()) {
            return new AiEmbeddings(List.of(), AiTokenUsage.ZERO);
        }

        Map<String, Object> body =
                provider.embeddingBody(embeddingModel, inputs, embeddingDimensions);

        JsonNode response = post(textClient, provider.embeddingsUrl(baseUrl), body,
                AiFeature.EMBEDDING);

        AiTokenUsage tokens = provider.readUsage(response);
        usage.record(userId, AiFeature.EMBEDDING, embeddingModel, tokens);

        List<float[]> vectors = provider.readEmbeddings(response);
        if (vectors.size() != inputs.size()) {
            throw new AiUnavailableException("Fornecedor devolveu " + vectors.size()
                    + " vetores para " + inputs.size() + " entradas");
        }
        // Vetor de dimensão errada corromperia o índice: melhor falhar agora.
        for (float[] vector : vectors) {
            if (vector.length != embeddingDimensions) {
                throw new AiUnavailableException("Vetor com " + vector.length
                        + " dimensões, esperado " + embeddingDimensions);
            }
        }
        return new AiEmbeddings(vectors, tokens);
    }

    // ── HTTP ─────────────────────────────────────────────────────────────

    /**
     * POST com repetição do que é transitório. 4xx que não seja 429 (chave
     * inválida, payload recusado) falha de imediato: repetir só queimaria
     * tempo e abriria o circuit breaker mais devagar.
     */
    private JsonNode post(RestClient client, String url, Map<String, Object> body, AiFeature feature) {
        int attempts = Math.max(0, props.getMaxRetries()) + 1;
        RuntimeException last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri(url)
                        .headers(h -> {
                            h.setBearerAuth(props.getApiKey());
                            h.setContentType(MediaType.APPLICATION_JSON);
                            h.setAccept(List.of(MediaType.APPLICATION_JSON));
                        })
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                if (response == null) {
                    throw new AiUnavailableException("Corpo vazio na resposta do provedor de IA");
                }
                return response;
            } catch (HttpStatusCodeException e) {
                HttpStatusCode status = e.getStatusCode();
                if (provider.isOutOfCredit(status.value(), e.getResponseBodyAsString())) {
                    // 429 por saldo zerado não é excesso de tráfego: repetir só
                    // gasta tempo e mantém o circuito fechado por mais tempo do
                    // que deveria. Falha na hora, com uma mensagem acionável.
                    log.error("IA {}: conta do provedor sem crédito — a integração fica "
                            + "em modo demonstração até haver saldo", feature);
                    throw new AiUnavailableException("Conta do provedor de IA sem crédito", e);
                }
                if (!isRetryable(status) || attempt == attempts) {
                    log.warn("IA {}: provedor respondeu {} (tentativa {}/{})",
                            feature, status.value(), attempt, attempts);
                    throw new AiUnavailableException("Provedor de IA respondeu " + status.value(), e);
                }
                last = e;
                sleep(retryDelay(e, attempt));
            } catch (ResourceAccessException e) {
                if (attempt == attempts) {
                    log.warn("IA {}: falha de rede/timeout (tentativa {}/{})", feature, attempt, attempts);
                    throw new AiUnavailableException("Provedor de IA inacessível", e);
                }
                last = e;
                sleep(retryDelay(null, attempt));
            }
        }
        throw new AiUnavailableException("Provedor de IA indisponível", last);
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }


    /** {@code Retry-After} do provedor quando houver; senão backoff com jitter. */
    private static Duration retryDelay(HttpStatusCodeException e, int attempt) {
        if (e != null && e.getResponseHeaders() != null) {
            String header = e.getResponseHeaders().getFirst("Retry-After");
            if (header != null && !header.isBlank()) {
                try {
                    Duration asked = Duration.ofSeconds((long) Double.parseDouble(header.trim()));
                    return asked.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : asked;
                } catch (NumberFormatException ignored) {
                    // Cabeçalho em formato de data: cai no backoff padrão.
                }
            }
        }
        long base = BACKOFF_BASE_MS * (1L << (attempt - 1));
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(100, 300));
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Espera entre tentativas interrompida", e);
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new AiUnavailableException("IA não configurada");
        }
    }
}
