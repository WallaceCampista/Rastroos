package com.rastroos.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sessão ativa de um usuário, exibida no detalhe do admin. O token nunca é
 * exposto — só o hash existe no banco e nem esse cruza a camada Web.
 */
@Schema(description = "Sessão ativa de um usuário (sem expor o token)")
public record UserSessionDto(
        UUID id,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastSeenAt
) {
}
