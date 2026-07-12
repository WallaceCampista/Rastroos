package com.rastroos.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalhe de um usuário para o admin: dados de conta + sessões ativas +
 * histórico recente de login. {@code self} indica que o admin está olhando a
 * própria conta (usado para desabilitar ações de auto-destruição na UI).
 */
@Schema(description = "Detalhe de um usuário: conta, sessões ativas e histórico de login")
public record UserDetailView(
        UUID id,
        String name,
        String email,
        boolean emailVerified,
        UserRole role,
        UserStatus status,
        String preferredLocale,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        boolean passwordMustChange,
        boolean self,
        List<UserSessionDto> sessions,
        List<LoginAttemptDto> loginHistory,
        UUID accessesUserId,
        String targetName
) {
}
