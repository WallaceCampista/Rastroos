package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Uma linha de "Movimentações" no detalhe de um investimento:
 * o mês, o rendimento estimado, o aporte (delta − rendimento) e o saldo
 * resultante. Derivado dos snapshots de {@code InvestmentHistory}.
 */
public record InvestmentMovementDto(
        String monthLabel,
        BigDecimal income,
        BigDecimal deposit,
        BigDecimal balance
) {
}
