package com.rastroos.web.dto;

import java.util.List;

/**
 * Snapshot da tela /app/compare: 6 meses até o selecionado (ordem
 * cronológica) com receita, gasto, saldo e aporte, mais os indicadores
 * da taxa de poupança.
 *
 * @param months           6 meses em ordem cronológica
 * @param target           meta de poupança em % (linha-alvo do gráfico)
 * @param avgSavingsRate   média das taxas dos meses com receita ({@code null} se nenhum)
 * @param monthsAboveTarget quantos meses ficaram &ge; {@code target}
 * @param monthsCounted    quantos meses tiveram receita (base da média)
 * @param bestMonthLabel   rótulo do mês de melhor taxa ({@code null} se nenhum)
 * @param bestMonthRate    taxa do melhor mês ({@code null} se nenhum)
 */
public record CompareModel(
        List<MonthSummaryDto> months,
        int target,
        Integer avgSavingsRate,
        int monthsAboveTarget,
        int monthsCounted,
        String bestMonthLabel,
        Integer bestMonthRate
) {
}
