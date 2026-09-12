package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha exibida em /app/income (e no REST /api/v1/incomes).
 *
 * <p>{@code sourceId} não-nulo marca o lançamento como parte de uma receita
 * recorrente — é o que faz a exclusão oferecer "tudo / deste mês em diante".
 * {@code received} separa o programado do que de fato caiu na conta.
 * Os campos de categoria só aparecem em registros antigos: receita nova não
 * tem categoria.
 */
public record IncomeDto(
        UUID id,
        String source,
        UUID sourceId,
        BigDecimal amount,
        LocalDate incomeDate,
        String categoryId,
        String categoryName,
        String categoryColorHex,
        String note,
        boolean received
) {
    /** {@code true} quando o lançamento veio de uma fonte recorrente cadastrada. */
    public boolean recurring() {
        return sourceId != null;
    }
}
