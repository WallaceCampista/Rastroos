package com.rastroos.web.dto;

/**
 * Filtros da tela /app/users: status (opcional) e busca por nome/email.
 * Os campos preservam o que o admin digitou, para re-render dos controles.
 */
public record UserFilter(String status, String search) {
}
