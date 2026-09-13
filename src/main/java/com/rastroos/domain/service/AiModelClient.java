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
import java.util.stream.Collectors;

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
    private final AiUsageRecorder usage;

    /** Um motor por fornecedor com credencial, montado no boot. */
    private final Map<String, AiEngine> engines;

    /** Qual deles está ativo. {@code null} nos testes, que fixam um motor só. */
    private final AiProviderSetting setting;
    private final AiEngine fixed;

    @Autowired
    public AiModelClient(AiProperties props, List<AiProvider> providers,
                         AiUsageRecorder usage, AiProviderSetting setting) {
        this.props = props;
        this.usage = usage;
        this.setting = setting;
        this.fixed = null;

        Map<String, AiEngine> built = new LinkedHashMap<>();
        for (AiProvider provider : providers) {
            built.put(provider.id(), engineFor(props, provider));
        }
        this.engines = Map.copyOf(built);

        String enabled = built.values().stream()
                .filter(AiEngine::enabled)
                .map(AiEngine::id)
                .collect(Collectors.joining(", "));
        if (enabled.isEmpty()) {
            log.info("IA em modo demonstração (nenhum fornecedor com chave configurada)");
        } else {
            log.info("IA habilitada. Fornecedores com credencial: {}", enabled);
        }
    }

    /** Um fornecedor só, montado a partir da configuração — para os testes. */
    AiModelClient(AiProperties props, AiProvider provider, AiUsageRecorder usage) {
        this(props, provider, usage, null, null);
    }

    /**
     * Construtor com os clientes HTTP já prontos — usado pelos testes, que
     * apontam para um servidor simulado e fixam um único fornecedor.
     */
    AiModelClient(AiProperties props, AiProvider provider, AiUsageRecorder usage,
                  RestClient textClient, RestClient visionClient) {
        this.props = props;
        this.usage = usage;
        this.setting = null;
        AiEngine engine = engineFor(props, provider);
        if (textClient != null || visionClient != null) {
            engine = new AiEngine(provider, engine.apiKey(), engine.baseUrl(),
                    engine.chatModel(), engine.embeddingModel(), engine.embeddingDimensions(),
                    textClient, visionClient);
        }
        this.fixed = engine;
        this.engines = Map.of(provider.id(), engine);
    }

    /** Resolve URL, modelos e clientes daquele fornecedor a partir da config. */
    private static AiEngine engineFor(AiProperties props, AiProvider provider) {
        String apiKey = props.apiKeyFor(provider.id());
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        String embeddingModel = props.getEmbedding().getModel() == null
                || props.getEmbedding().getModel().isBlank()
                ? provider.defaultEmbeddingModel()
                : props.getEmbedding().getModel().trim();
        int dimensions = props.getEmbedding().getDimensions() > 0
                ? props.getEmbedding().getDimensions()
                : provider.defaultEmbeddingDimensions();
        return new AiEngine(
                provider,
                hasKey ? apiKey : "",
                props.resolvedBaseUrl(provider.defaultBaseUrl()),
                props.resolvedModel(provider.defaultChatModel()),
                embeddingModel,
                dimensions,
                hasKey ? build(props, props.getReadTimeoutMs()) : null,
                hasKey ? build(props, props.getVision().getReadTimeoutMs()) : null);
    }

    /**
     * O motor ativo. A escolha do administrador vale enquanto o fornecedor
     * escolhido tiver credencial; sem ela, cai em qualquer um que tenha — é
     * melhor responder pelo outro motor do que desligar a IA por engano.
     */
    private AiEngine active() {
        if (fixed != null) {
            return fixed;
        }
        AiEngine chosen = engines.get(setting.current());
        if (chosen != null && chosen.enabled()) {
            return chosen;
        }
        return engines.values().stream()
                .filter(AiEngine::enabled)
                .findFirst()
                .orElse(chosen != null ? chosen : engines.values().iterator().next());
    }

    /** Modelo de texto em uso — rótulo do livro-caixa e da chave do cache. */
    public String chatModel() {
        return active().chatModel();
    }

    public String embeddingModel() {
        return active().embeddingModel();
    }

    public int embeddingDimensions() {
        return active().embeddingDimensions();
    }

    /** Fornecedor ativo, para quem precisa montar partes de mensagem. */
    public AiProvider provider() {
        return active().provider();
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
        return active().enabled();
    }

    /**
     * Chat completions. {@code responseFormat} nulo = texto livre; preenchido
     * com um JSON Schema estrito, o provedor devolve JSON válido por contrato
     * (usado pela leitura de documentos).
     */
    public AiCompletion chat(AiFeature feature, UUID userId, List<Map<String, Object>> messages,
                             int maxTokens, double temperature, Map<String, Object> responseFormat) {
        AiEngine engine = requireEnabled();
        AiProvider provider = engine.provider();

        Map<String, Object> body = provider.chatBody(
                engine.chatModel(), messages, maxTokens, temperature, responseFormat);

        RestClient client = feature == AiFeature.VISION ? engine.visionClient() : engine.textClient();
        JsonNode response = post(engine, client, provider.chatUrl(engine.baseUrl()), body, feature);

        AiTokenUsage tokens = provider.readUsage(response);
        usage.record(userId, feature, engine.chatModel(), tokens);

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
        AiEngine engine = requireEnabled();
        AiProvider provider = engine.provider();

        Map<String, Object> body = new LinkedHashMap<>(
                provider.chatBody(engine.chatModel(), messages, maxTokens, temperature, null));
        body.putAll(provider.streamingOptions());

        StringBuilder full = new StringBuilder();
        AiTokenUsage[] tokens = { AiTokenUsage.ZERO };

        try {
            engine.textClient().post()
                    .uri(provider.chatUrl(engine.baseUrl()))
                    .headers(h -> {
                        h.setBearerAuth(engine.apiKey());
                        h.setContentType(MediaType.APPLICATION_JSON);
                        h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                    })
                    .body(body)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw streamFailure(engine, feature, status, response);
                        }
                        readEventStream(engine, response.getBody(), full, tokens, onDelta);
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

        usage.record(userId, feature, engine.chatModel(), tokens[0]);

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
    private void readEventStream(AiEngine engine, java.io.InputStream in, StringBuilder full,
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
                String delta = engine.provider().readStreamDelta(chunk);
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    onDelta.accept(delta);
                }
                AiTokenUsage chunkUsage = engine.provider().readUsage(chunk);
                if (chunkUsage != null && chunkUsage.totalTokens() > 0) {
                    tokens[0] = chunkUsage;
                }
            }
        }
    }

    /** Erro HTTP no início do streaming, com a mesma leitura de "sem crédito". */
    private AiUnavailableException streamFailure(AiEngine engine, AiFeature feature,
                                                 HttpStatusCode status,
                                                 org.springframework.http.client.ClientHttpResponse response) {
        String bodyText = "";
        try {
            bodyText = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Sem corpo legível: o status já basta para a mensagem.
        }
        if (engine.provider().isOutOfCredit(status.value(), bodyText)) {
            log.error("IA {}: conta do provedor {} sem crédito", feature, engine.id());
            return new AiUnavailableException("Conta do provedor de IA sem crédito");
        }
        log.warn("IA {}: provedor respondeu {} ao abrir o streaming", feature, status.value());
        return new AiUnavailableException("Provedor de IA respondeu " + status.value());
    }

    /** Vetoriza um lote de textos; a ordem da saída espelha a da entrada. */
    public AiEmbeddings embed(UUID userId, List<String> inputs) {
        AiEngine engine = requireEnabled();
        if (inputs.isEmpty()) {
            return new AiEmbeddings(List.of(), AiTokenUsage.ZERO);
        }
        AiProvider provider = engine.provider();
        int dimensions = engine.embeddingDimensions();

        Map<String, Object> body =
                provider.embeddingBody(engine.embeddingModel(), inputs, dimensions);

        JsonNode response = post(engine, engine.textClient(),
                provider.embeddingsUrl(engine.baseUrl()), body, AiFeature.EMBEDDING);

        AiTokenUsage tokens = provider.readUsage(response);
        usage.record(userId, AiFeature.EMBEDDING, engine.embeddingModel(), tokens);

        List<float[]> vectors = provider.readEmbeddings(response);
        if (vectors.size() != inputs.size()) {
            throw new AiUnavailableException("Fornecedor devolveu " + vectors.size()
                    + " vetores para " + inputs.size() + " entradas");
        }
        // Vetor de dimensão errada corromperia o índice: melhor falhar agora.
        for (float[] vector : vectors) {
            if (vector.length != dimensions) {
                throw new AiUnavailableException("Vetor com " + vector.length
                        + " dimensões, esperado " + dimensions);
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
    private JsonNode post(AiEngine engine, RestClient client, String url,
                          Map<String, Object> body, AiFeature feature) {
        int attempts = Math.max(0, props.getMaxRetries()) + 1;
        RuntimeException last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri(url)
                        .headers(h -> {
                            h.setBearerAuth(engine.apiKey());
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
                if (engine.provider().isOutOfCredit(status.value(), e.getResponseBodyAsString())) {
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

    /** O motor ativo, ou falha se nenhum tiver credencial. */
    private AiEngine requireEnabled() {
        AiEngine engine = active();
        if (!engine.enabled()) {
            throw new AiUnavailableException("IA não configurada");
        }
        return engine;
    }
}
