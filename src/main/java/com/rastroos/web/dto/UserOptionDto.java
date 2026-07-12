package com.rastroos.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Opção enxuta de usuário para selects (ex.: escolher o usuário-alvo de um
 * acessor). Sem dados sensíveis.
 */
@Schema(description = "Opção de usuário para seleção (id + nome + email)")
public record UserOptionDto(
        UUID id,
        String name,
        String email
) {
}
