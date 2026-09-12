package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * KPIs do mês atual do dashboard. Todos em {@link BigDecimal} reais (R$),
 * convertidos de centavos pelo Service.
 *
 * @param received  o que já caiu na conta (receita confirmada)
 * @param toReceive o que está previsto para o mês e ainda não foi confirmado
 */
public record DashboardKpisDto(
        BigDecimal received,
        BigDecimal toReceive,
        BigDecimal spent,
        BigDecimal paid,
        BigDecimal toPay,
        BigDecimal balance,
        BigDecimal invested
) {
}
