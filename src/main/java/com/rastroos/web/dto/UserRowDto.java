package com.rastroos.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Linha da tabela de usuários (admin). Nunca expõe hash de senha nem qualquer
 * dado sensível — apenas o necessário para listagem e navegação.
 */
@Schema(description = "Resumo de um usuário na listagem administrativa")
public record UserRowDto(
        UUID id,
        String name,
        String email,
        boolean emailVerified,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt,
        long activeSessions
) {
}
