package com.rastroos.web.dto;

/**
 * Filtros da listagem de chamados, ecoados de volta ao template para manter
 * o estado dos controles. {@code status}/{@code priority}/{@code category}
 * nulos = todos. {@code scope} = {@code all} (admin vê tudo) ou {@code mine}
 * (só os do próprio usuário; padrão para não-admin).
 */
public record SupportFilter(String status, String priority, String category,
                            String scope, String search) {

    public static SupportFilter empty() {
        return new SupportFilter(null, null, null, null, null);
    }

    public boolean isMine() {
        return "mine".equalsIgnoreCase(scope);
    }
}
