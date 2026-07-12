package com.rastroos.web.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada do histórico de login exibida no detalhe do usuário (admin).
 */
@Schema(description = "Tentativa de login registrada (sucesso/falha) no histórico")
public record LoginAttemptDto(
        String ipAddress,
        boolean success,
        Instant attemptedAt
) {
}
