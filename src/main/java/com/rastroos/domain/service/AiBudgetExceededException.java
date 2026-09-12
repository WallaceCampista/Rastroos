package com.rastroos.domain.service;

/**
 * Teto diário de IA do usuário atingido. Subclasse de
 * {@link AiUnavailableException} para que todos os caminhos de contingência já
 * existentes a tratem, mas distinguível quando vale explicar ao usuário que o
 * limite é do dia — e não uma falha do provedor.
 */
public class AiBudgetExceededException extends AiUnavailableException {

    private static final long serialVersionUID = 1L;

    public AiBudgetExceededException(String message) {
        super(message);
    }
}
