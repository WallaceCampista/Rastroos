package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Uma linha de "Movimentações" no detalhe de um investimento — um evento do
 * livro-razão: saldo inicial, aporte ou resgate, com o valor (positivo) e o
 * saldo resultante. Derivado de {@code InvestmentMovement}.
 */
public record InvestmentMovementDto(
        String dateLabel,
        String kind,
        BigDecimal amount,
        BigDecimal balance
) {
}
