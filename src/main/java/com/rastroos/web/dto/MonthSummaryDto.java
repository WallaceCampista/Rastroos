package com.rastroos.web.dto;

import java.math.BigDecimal;

/**
 * Resumo agregado de um mês, compartilhado pelas telas de Relatórios e
 * Comparativo. Todos os valores monetários já em {@link BigDecimal}
 * (reais), convertidos de centavos pelo Service.
 *
 * @param yearMonth    chave {@code "YYYY-MM"}
 * @param label        rótulo curto localizado do mês (ex.: {@code "Mai"})
 * @param received     total de receitas no mês
 * @param spent        total lançado (gastos)
 * @param paid         total já pago
 * @param toPay        {@code spent - paid} (mínimo 0)
 * @param fixed        parcela dos gastos marcada como fixa
 * @param oneTime      {@code spent - fixed} (gastos pontuais)
 * @param net          {@code received - spent} (saldo previsto; pode ser negativo)
 * @param invested     aporte estimado do mês (delta de investimentos − rendimento)
 * @param savingsRate  % da receita que sobrou; {@code null} quando não houve receita
 * @param current      {@code true} se este é o mês selecionado (destaque na tabela)
 */
public record MonthSummaryDto(
        String yearMonth,
        String label,
        BigDecimal received,
        BigDecimal spent,
        BigDecimal paid,
        BigDecimal toPay,
        BigDecimal fixed,
        BigDecimal oneTime,
        BigDecimal net,
        BigDecimal invested,
        Integer savingsRate,
        boolean current
) {
}
