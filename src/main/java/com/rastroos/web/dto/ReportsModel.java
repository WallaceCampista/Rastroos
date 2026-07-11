package com.rastroos.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot da tela /app/reports para o mês selecionado:
 * <ul>
 *   <li>{@code current} — totais do mês (pago vs a pagar, fixo vs pontual);</li>
 *   <li>{@code byCategory} — gastos por categoria (donut + peso);</li>
 *   <li>{@code byAccount} — gastos por conta/cartão (donut);</li>
 *   <li>{@code trailing6} — 6 meses até o selecionado (linha fixo vs variável).</li>
 * </ul>
 * As fatias de categoria e de conta reusam {@link CategoryBreakdownDto}
 * (id, nome, cor e valor já resolvidos).
 */
public record ReportsModel(
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        MonthSummaryDto current,
        List<CategoryBreakdownDto> byCategory,
        List<CategoryBreakdownDto> byAccount,
        List<MonthSummaryDto> trailing6
) {
}
