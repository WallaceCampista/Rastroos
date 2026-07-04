package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * KPIs do mês atual do dashboard. Todos em {@link BigDecimal} reais (R$),
 * convertidos de centavos pelo Service.
 */
public record DashboardKpisDto(
        BigDecimal received,
        BigDecimal spent,
        BigDecimal paid,
        BigDecimal toPay,
        BigDecimal balance
) {
}
