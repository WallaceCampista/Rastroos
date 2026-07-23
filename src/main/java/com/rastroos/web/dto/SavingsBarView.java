package com.rastroos.web.dto;

/**
 * Geometria pré-calculada de uma barra do gráfico "Taxa de poupança por mês".
 * A posição é dada em porcentagem da área do gráfico (a partir do topo), para
 * que a barra possa cair abaixo da linha do zero quando a taxa é negativa —
 * espelhando o cálculo do protótipo React ({@code SavingsRateBars}).
 *
 * @param label        rótulo curto do mês (ex.: {@code "Mai"})
 * @param rate         taxa de poupança em % ({@code null} quando não houve receita)
 * @param barTopPct    topo da barra, em % da área (0 = topo do gráfico)
 * @param barHeightPct altura da barra, em % da área
 * @param tone         cor semântica: {@code ok} | {@code warn} | {@code bad} | {@code muted}
 * @param negative     {@code true} quando a taxa é negativa (barra cresce para baixo)
 */
public record SavingsBarView(
        String label,
        Integer rate,
        double barTopPct,
        double barHeightPct,
        String tone,
        boolean negative
) {
}
