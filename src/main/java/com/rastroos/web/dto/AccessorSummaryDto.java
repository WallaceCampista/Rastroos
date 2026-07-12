package com.rastroos.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.rastroos.domain.entity.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumo de uma conta acessor vinculada a um titular. Usado tanto na
 * transparência para o titular (quantos acessores tem, quem são) quanto na
 * tela de detalhe do admin. {@code valuesMasked} indica se o titular ocultou
 * os valores para este acessor.
 */
@Schema(description = "Resumo de um acessor vinculado a um titular")
public record AccessorSummaryDto(
        UUID id,
        String name,
        String email,
        UserStatus status,
        boolean valuesMasked,
        Instant createdAt
) {
}
