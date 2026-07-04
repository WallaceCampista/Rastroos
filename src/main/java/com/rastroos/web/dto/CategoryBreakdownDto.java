package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Um fatia do gráfico de "gastos por categoria" — nome localizado e cor já
 * resolvidos a partir da entity {@code Category}.
 */
public record CategoryBreakdownDto(
        String categoryId,
        String name,
        String colorHex,
        BigDecimal amount
) {
}
