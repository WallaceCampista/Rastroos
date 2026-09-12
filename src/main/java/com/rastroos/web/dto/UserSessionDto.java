package com.rastroos.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sessão ativa de um usuário, exibida no histórico de login do admin. O token
 * nunca é exposto — só o hash existe no banco e nem esse cruza a camada Web.
 * {@code device} já vem reduzido ao SO (ver {@code DeviceLabel}): o User-Agent
 * cru não cabe na tabela e não acrescenta nada para quem lê.
 */
@Schema(description = "Sessão ativa de um usuário (sem expor o token)")
public record UserSessionDto(
        UUID id,
        String device,
        String ipAddress,
        Instant createdAt,
        Instant lastSeenAt
) {
}
