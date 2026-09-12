package com.rastroos.domain.service;

import java.time.YearMonth;
import java.util.UUID;

/**
 * De quem é a conversa e de quem são os dados que o Alfredo pode ler.
 *
 * <p>Os dois quase sempre coincidem, mas não quando um ACESSOR está operando a
 * conta de outra pessoa: a conversa fica gravada na conta que perguntou
 * ({@code chatOwnerId}, para aparecer no histórico dela) enquanto os números
 * vêm do titular ({@code dataOwnerId}). Separar os dois em campos nomeados
 * — em vez de um {@code UUID} solto — é o que impede trocar um pelo outro num
 * refactor e vazar dado entre contas (§2.2).
 *
 * @param chatOwnerId conta autenticada, dona da conversa (nunca nula)
 * @param dataOwnerId dono dos dados financeiros, ou {@code null} quando o
 *                    Alfredo não deve ver número algum (acessor com valores
 *                    mascarados pelo titular)
 * @param period      mês de referência do dossiê
 */
public record ChatScope(UUID chatOwnerId, UUID dataOwnerId, YearMonth period) {

    public ChatScope {
        if (chatOwnerId == null) {
            throw new IllegalArgumentException("chatOwnerId é obrigatório");
        }
    }

    /** Conversa comum: mesma pessoa pergunta e é dona dos dados. */
    public static ChatScope own(UUID userId, YearMonth period) {
        return new ChatScope(userId, userId, period);
    }

    /** Conversa sem acesso a números (valores mascarados pelo titular). */
    public static ChatScope masked(UUID chatOwnerId, YearMonth period) {
        return new ChatScope(chatOwnerId, null, period);
    }

    public boolean hasData() {
        return dataOwnerId != null;
    }
}
