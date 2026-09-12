package com.rastroos.domain.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Acesso aos circuit breakers da IA, um por funcionalidade
 * ({@code ai-chat}, {@code ai-insight}, {@code ai-embedding},
 * {@code ai-vision}) — assim a visão fora do ar não cala o chat.
 *
 * <p><strong>Por que programático e não {@code @CircuitBreaker}</strong>: com a
 * anotação, a proteção só existe quando a chamada passa pelo proxy do Spring, e
 * o {@code fallbackMethod} simplesmente não roda numa chamada interna ou num
 * teste unitário — a exceção vazaria para o usuário justamente no caminho que
 * deveria protegê-lo. Aqui o comportamento é o mesmo dentro e fora do
 * contêiner, e cada serviço decide explicitamente o que fazer na falha.
 */
@Component
public class AiCircuitBreakers {

    public static final String CHAT = "ai-chat";
    public static final String INSIGHT = "ai-insight";
    public static final String EMBEDDING = "ai-embedding";
    public static final String VISION = "ai-vision";

    private final CircuitBreakerRegistry registry;

    public AiCircuitBreakers(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Executa sob o breaker de {@code name}. Propaga a exceção (é assim que a
     * falha é contabilizada); com o breaker aberto, falha de imediato sem
     * sequer tentar a rede.
     */
    public <T> T call(String name, Supplier<T> action) {
        return registry.circuitBreaker(name).executeSupplier(action);
    }
}
