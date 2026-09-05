package com.rastroos.web.dto;

/**
 * Resumo do Alfredo para uma tela: o texto exibido no balão flutuante e o
 * contexto que o gerou (tela + período), para o cliente saber se o resumo
 * ainda casa com o que está na tela.
 *
 * @param screen      chave da tela ({@code dashboard}, {@code cards}, …)
 * @param screenLabel rótulo exibível da tela
 * @param period      {@code YYYY-MM} do resumo, ou {@code null} quando a tela
 *                    não depende do mês
 * @param text        o resumo em si (situação atual + cuidados)
 * @param aiGenerated {@code true} quando veio do motor de IA;
 *                    {@code false} quando é o resumo local determinístico
 */
public record InsightDto(
        String screen,
        String screenLabel,
        String period,
        String text,
        boolean aiGenerated
) {
}
