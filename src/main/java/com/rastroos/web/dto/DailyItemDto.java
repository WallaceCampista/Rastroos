package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Um lançamento de um dia, para o tooltip do gráfico "Gastos no mês"
 * (mostra o nome da conta ao passar o mouse no nó do dia).
 */
public record DailyItemDto(
        String account,
        String description,
        BigDecimal amount
) {
}
