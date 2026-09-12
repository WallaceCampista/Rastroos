package com.rastroos.domain.service;

import java.util.UUID;

/**
 * Algo financeiro do usuário mudou (lançamento, receita, conta, investimento).
 *
 * <p>Publicado pelos services de escrita e consumido <em>depois do commit</em>
 * por {@link UserDataVersionService}: uma escrita desfeita por rollback não
 * marca nada como sujo, e portanto não dispara geração nenhuma.
 *
 * <p>É o único gatilho de consumo de IA para os resumos de tela. Sem escrita,
 * nada é regerado — é isso que evita a cobrança diária de quem passou o mês
 * sem lançar nada.
 */
public record UserDataChangedEvent(UUID userId) {

    public UserDataChangedEvent {
        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
    }
}
