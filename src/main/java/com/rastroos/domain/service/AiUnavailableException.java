package com.rastroos.domain.service;

/**
 * IA indisponível: desligada por configuração, fora do ar, sem orçamento ou
 * devolvendo resposta inutilizável.
 *
 * <p>Nunca chega ao usuário como erro: os chamadores a traduzem em modo
 * demonstração (chat) ou no resumo local determinístico (balão da tela). É
 * {@code RuntimeException} de propósito, para que o circuit breaker do
 * Resilience4j a conte como falha.
 */
public class AiUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
