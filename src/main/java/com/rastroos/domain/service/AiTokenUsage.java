package com.rastroos.domain.service;

/**
 * Tokens consumidos por uma chamada ao provedor. Só contagem — nunca conteúdo.
 */
public record AiTokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static final AiTokenUsage ZERO = new AiTokenUsage(0, 0, 0);

    public AiTokenUsage plus(AiTokenUsage other) {
        return new AiTokenUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens);
    }
}
