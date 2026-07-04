package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.rastroos.domain.entity.enums.InvestmentKind;

/**
 * KPIs da tela /app/investments:
 * <ul>
 *   <li>{@code totalInvested} — soma de todos os {@code amountCents}.</li>
 *   <li>{@code totalGoals} — soma das metas dos cofrinhos.</li>
 *   <li>{@code piggyProgress} — quanto já foi atingido das metas (em
 *       relação a {@code totalGoals}); 0..100. {@code null} quando não há
 *       metas cadastradas.</li>
 *   <li>{@code monthlyReturn} — rendimento mensal estimado somando a
 *       coluna {@code monthly_return_cents}.</li>
 *   <li>{@code byKind} — total investido por tipo, em {@link BigDecimal}.</li>
 * </ul>
 */
public record PortfolioSummaryDto(
        BigDecimal totalInvested,
        BigDecimal totalGoals,
        Integer piggyProgress,
        BigDecimal monthlyReturn,
        Map<InvestmentKind, BigDecimal> byKind
) {
}
