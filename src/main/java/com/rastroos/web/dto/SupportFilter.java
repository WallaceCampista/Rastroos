package com.rastroos.web.dto;

/**
 * Filtros da listagem de chamados, ecoados de volta ao template para manter
 * o estado dos controles. {@code status} nulo = todos.
 */
public record SupportFilter(String status, String search) {

    public static SupportFilter empty() {
        return new SupportFilter(null, null);
    }
}
