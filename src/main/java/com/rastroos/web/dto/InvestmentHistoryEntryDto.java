package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Ponto do histórico mensal de um investimento. {@code yearMonth} sempre
 * no formato {@code YYYY-MM}.
 */
public record InvestmentHistoryEntryDto(
        String yearMonth,
        BigDecimal amount
) {
}
