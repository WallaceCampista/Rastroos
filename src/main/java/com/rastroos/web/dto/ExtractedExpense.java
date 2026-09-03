package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Campos extraídos de um documento/foto para pré-preencher o lançamento.
 * Todos são <strong>sugestões editáveis</strong>: o usuário valida e ajusta
 * antes de salvar. Campos que a extração não conseguiu ler vêm {@code null}
 * (ex.: {@code amount} no modo demonstração).
 *
 * @param demo {@code true} quando a extração é um stub (IA de visão ainda não
 *             configurada) — a UI mostra um aviso de "modo demonstração".
 */
public record ExtractedExpense(
        String description,
        BigDecimal amount,
        LocalDate dueDate,
        boolean fixed,
        String categoryId,
        /** 4 últimos dígitos lidos (foto da notinha), ou {@code null}. */
        String last4,
        /** Conta casada pelos {@code last4} (cartão), ou {@code null}. */
        UUID accountId,
        boolean demo
) {
}
