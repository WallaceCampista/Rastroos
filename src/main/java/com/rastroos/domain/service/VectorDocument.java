package com.rastroos.domain.service;

import java.time.LocalDate;

/**
 * Um trecho de texto do usuário pronto para virar vetor.
 *
 * @param kind        origem ({@code TRANSACTION}, {@code INCOME}, …)
 * @param refId       id da linha de origem, como texto
 * @param content     texto indexado (já resumido/truncado)
 * @param contentHash SHA-256 de {@code content} — texto igual não é re-embeddado
 * @param occurredOn  data do fato, quando existir
 * @param amountCents valor em centavos, quando existir
 */
public record VectorDocument(
        String kind,
        String refId,
        String content,
        String contentHash,
        LocalDate occurredOn,
        Long amountCents
) {
    /** Chave de comparação com o que já está indexado. */
    public String key() {
        return kind + '|' + refId;
    }
}
