package com.rastroos.web.dto;

/**
 * Filtros opcionais da listagem de receitas. Qualquer campo nulo ou em
 * branco é ignorado.
 */
public record IncomeFilter(
        String categoryId,
        String search
) {

    public static IncomeFilter empty() {
        return new IncomeFilter(null, null);
    }
}
