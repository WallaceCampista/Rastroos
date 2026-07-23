package com.rastroos.web.dto;

import java.util.List;

/**
 * Detalhe de uma conta no mês: resumo + lançamentos (linhas ricas com
 * categoria/parcela/status) + gastos por categoria — espelha o
 * {@code AccountDetailPanel} do protótipo.
 */
public record AccountDetailView(
        AccountSummaryDto account,
        int year,
        List<TransactionDto> transactions,
        List<CategoryBreakdownDto> categories
) {
}
