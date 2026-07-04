package com.rastroos.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot do dashboard para a tela: KPIs do mês + séries para gráficos +
 * próximos vencimentos em aberto + top contas do mês. Imutável.
 */
public record DashboardModel(
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        long accountsCount,
        long entriesCount,
        DashboardKpisDto kpis,
        List<BalancePointDto> balanceSeries,
        List<CategoryBreakdownDto> byCategory,
        List<UpcomingTransactionDto> upcoming,
        List<AccountSummaryDto> topAccounts
) {
}
