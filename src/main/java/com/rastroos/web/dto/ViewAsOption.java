package com.rastroos.web.dto;

import java.util.UUID;

/**
 * Opção do select "ver como" (admin): um usuário cujos dados o admin pode visualizar.
 */
public record ViewAsOption(UUID id, String name, String email) {
}
