package com.rastroos.domain.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.rastroos.config.AlfredoProperties;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.ChatMessageRole;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Cliente do "Alfredo" (gerente financeiro IA).
 *
 * <p>Por padrão roda em <strong>modo demonstração</strong> (sem
 * {@code alfredo.base-url}): devolve uma resposta canned, sem tráfego externo.
 * Configurando o endpoint, faz a chamada real via {@link RestClient} com
 * timeouts de conexão/leitura, protegida pelo circuit breaker
 * {@code alfredo} (Resilience4j) — se o provedor cair ou estourar o tempo,
 * o {@link #fallback} entra no lugar.
 */
@Component
public class AlfredoAiClient {

    private static final Logger log = LoggerFactory.getLogger(AlfredoAiClient.class);

    private final AlfredoProperties props;
    private final RestClient restClient; // null quando desligado (stub)

    public AlfredoAiClient(AlfredoProperties props) {
        this.props = props;
        this.restClient = props.isEnabled() ? buildClient(props) : null;
    }

    private static RestClient buildClient(AlfredoProperties p) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(p.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(p.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(p.getBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    /**
     * Gera a resposta do Alfredo para {@code userMessage}, considerando o
     * histórico recente. Protegido por circuit breaker; falhas caem no
     * {@link #fallback}.
     */
    @CircuitBreaker(name = "alfredo", fallbackMethod = "fallback")
    public String reply(String userMessage, List<ChatMessage> history) {
        if (restClient == null) {
            return stubReply(userMessage);
        }
        return callRemote(userMessage, history, props.getSystemPrompt());
    }

    /**
     * Reescreve, na voz do Alfredo, o resumo de uma tela a partir dos números
     * que o servidor já calculou ({@code prompt}).
     *
     * <p>Devolve {@link Optional#empty()} quando não há IA configurada ou
     * quando o provedor falha (circuit breaker aberto, timeout, resposta
     * vazia): nesses casos o chamador usa o resumo local determinístico, que
     * já é montado a partir dos mesmos números.
     */
    @CircuitBreaker(name = "alfredo", fallbackMethod = "summarizeFallback")
    public Optional<String> summarize(String prompt) {
        if (restClient == null) {
            return Optional.empty();
        }
        return Optional.of(callRemote(prompt, List.of(), props.getInsightSystemPrompt()));
    }

    private String callRemote(String userMessage, List<ChatMessage> history, String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        int window = Math.max(0, props.getHistoryWindow());
        List<ChatMessage> recent = history.size() > window
                ? history.subList(history.size() - window, history.size())
                : history;
        for (ChatMessage m : recent) {
            String role = m.getRole() == ChatMessageRole.ASSISTANT ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of("model", props.getModel(), "messages", messages);

        Map<?, ?> response = restClient.post()
                .headers(h -> {
                    if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
                        h.setBearerAuth(props.getApiKey());
                    }
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        String content = extractContent(response);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Resposta vazia do provedor de IA");
        }
        return content.trim();
    }

    /** Extrai {@code choices[0].message.content} de uma resposta estilo OpenAI. */
    private static String extractContent(Map<?, ?> response) {
        if (response == null) return null;
        if (response.get("choices") instanceof List<?> choices
                && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> first
                && first.get("message") instanceof Map<?, ?> message) {
            Object content = message.get("content");
            return content == null ? null : content.toString();
        }
        return null;
    }

    /** Fallback do circuit breaker: mensagem amigável, sem vazar o erro cru. */
    @SuppressWarnings("unused")
    private String fallback(String userMessage, List<ChatMessage> history, Throwable t) {
        log.warn("Alfredo indisponível ({}), usando resposta de contingência", t.toString());
        return "No momento não consegui falar com o motor de IA. Enquanto isso, dê uma olhada "
                + "nos seus vencimentos do mês em Gastos e no saldo em Visão geral. "
                + "Tente novamente em alguns instantes.";
    }

    /** Fallback do circuit breaker do resumo: cai no texto local do servidor. */
    @SuppressWarnings("unused")
    private Optional<String> summarizeFallback(String prompt, Throwable t) {
        log.warn("Alfredo indisponível para resumo ({}), usando o resumo local", t.toString());
        return Optional.empty();
    }

    /**
     * Resposta de demonstração (sem IA configurada): acolhe a pergunta e
     * aponta para as telas do app. Determinística para facilitar os testes.
     */
    private String stubReply(String userMessage) {
        String trimmed = userMessage == null ? "" : userMessage.strip();
        String snippet = trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
        return "Olá! Sou o Alfredo, seu gerente financeiro. Estou em modo demonstração "
                + "(a IA ainda não foi conectada), mas já consigo te orientar.\n\n"
                + "Sobre \"" + snippet + "\": comece conferindo a Visão geral do mês, "
                + "priorize os vencimentos em aberto e registre suas receitas para o saldo ficar fiel. "
                + "Quando o motor de IA for configurado, respondo com análises personalizadas dos seus dados.";
    }
}
