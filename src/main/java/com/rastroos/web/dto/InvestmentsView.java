package com.rastroos.web.dto;

import java.util.List;

/**
 * Snapshot da tela /app/investments: cofrinhos (PIGGY) separados da
 * carteira (CDI/Treasury/LCI/etc.) + KPIs.
 */
public record InvestmentsView(
        List<InvestmentDto> piggies,
        List<InvestmentDto> portfolio,
        PortfolioSummaryDto summary,
        InvestmentChartData chart
) {
}
