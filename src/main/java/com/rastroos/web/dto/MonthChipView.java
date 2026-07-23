package com.rastroos.web.dto;

/**
 * Chip de mês do seletor de período (topbar). Um por mês do ano vigente.
 *
 * @param ym     chave {@code "YYYY-MM"} para navegação (?ym=)
 * @param label  rótulo curto localizado (ex.: {@code "Jan"})
 * @param status {@code paid|pending|negative|future|empty} — dirige ícone e cor
 * @param active {@code true} se é o mês selecionado
 */
public record MonthChipView(String ym, String label, String status, boolean active) {
}
