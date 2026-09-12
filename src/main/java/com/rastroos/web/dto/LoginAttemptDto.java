package com.rastroos.web.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada do histórico de login exibida para o admin. {@code device} já vem
 * reduzido ao sistema operacional (ver {@code DeviceLabel}) — o User-Agent cru
 * fica só no banco. Tentativas anteriores à coluna {@code user_agent}
 * (changelog 011) não têm o dado e chegam como "—".
 */
@Schema(description = "Tentativa de login registrada (sucesso/falha) no histórico")
public record LoginAttemptDto(
        String device,
        String ipAddress,
        boolean success,
        Instant attemptedAt
) {
}
