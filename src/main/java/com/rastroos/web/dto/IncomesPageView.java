package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Página paginada de receitas + total acumulado (em reais). Usada na tela
 * /app/income e no REST.
 */
public record IncomesPageView(
        List<IncomeDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal totalAmount
) {
}
