package com.rastroos.web.dto;

import java.util.List;

/**
 * Dados brutos de uma tela, já formatados como pares rótulo/valor, mais o
 * resumo determinístico calculado a partir deles.
 *
 * <p>Os números vêm sempre do {@code fallbackText} e das {@code lines} — a IA
 * só reescreve o texto a partir daí, nunca busca dado por conta própria. Isso
 * mantém o resumo verificável mesmo quando o motor de IA está ligado.
 *
 * @param screen       tela de origem
 * @param periodLabel  rótulo do período (ex.: {@code "setembro de 2026"}) ou
 *                     {@code null} nas telas sem mês
 * @param lines        pares {@code "Rótulo: valor"} usados como contexto da IA
 * @param fallbackText resumo determinístico (situação + cuidados)
 */
public record InsightFacts(
        InsightScreen screen,
        String periodLabel,
        List<String> lines,
        String fallbackText
) {
}
