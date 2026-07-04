package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha exibida em /app/income (e no REST /api/v1/incomes).
 * Inclui nome/cor da categoria já resolvidos para evitar joins na view.
 */
public record IncomeDto(
        UUID id,
        String source,
        BigDecimal amount,
        LocalDate incomeDate,
        String categoryId,
        String categoryName,
        String categoryColorHex,
        String note
) {
}
