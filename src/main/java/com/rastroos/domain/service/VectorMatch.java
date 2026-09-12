package com.rastroos.domain.service;

import java.time.LocalDate;

/**
 * Resultado da busca semântica: o trecho encontrado e o quanto ele se parece
 * com a pergunta ({@code score} = similaridade de cosseno, 0..1).
 */
public record VectorMatch(
        String kind,
        String refId,
        String content,
        LocalDate occurredOn,
        Long amountCents,
        double score
) {
}
