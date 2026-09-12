package com.rastroos.domain.service;

import java.util.List;

/** Vetores devolvidos pelo provedor, na mesma ordem das entradas. */
public record AiEmbeddings(List<float[]> vectors, AiTokenUsage usage) {
}
