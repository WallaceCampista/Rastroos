package com.rastroos.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Séries para os gráficos da tela de investimentos:
 * <ul>
 *   <li>{@code labels} — rótulos curtos dos 6 meses (ex.: {@code "Mai"})</li>
 *   <li>{@code totalCents} — patrimônio total por mês (para a onda do hero)</li>
 *   <li>{@code sparklineCents} — histórico por investimento (id → valores),
 *       para o mini-gráfico de cada linha da carteira</li>
 * </ul>
 * Valores em centavos; a conversão para reais acontece na serialização.
 */
public record InvestmentChartData(
        List<String> labels,
        List<Long> totalCents,
        Map<String, List<Long>> sparklineCents
) {
}
