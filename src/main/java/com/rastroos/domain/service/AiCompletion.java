package com.rastroos.domain.service;

/** Resposta de texto do provedor, com o custo em tokens que ela gerou. */
public record AiCompletion(String content, AiTokenUsage usage) {
}
