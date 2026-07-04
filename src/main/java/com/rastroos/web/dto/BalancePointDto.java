package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Um ponto da série diária do saldo no mês: dia (1..N) → saldo acumulado.
 */
public record BalancePointDto(
        int day,
        BigDecimal balance
) {
}
