package com.rastroos.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Página da tela /app/users: usuários + KPIs globais por status/role.
 */
@Schema(description = "Página de usuários com KPIs agregados por status e papel")
public record UserAdminListView(
        List<UserRowDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long totalCount,
        long activeCount,
        long pendingCount,
        long disabledCount,
        long adminCount
) {
}
