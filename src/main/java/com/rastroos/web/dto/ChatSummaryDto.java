package com.rastroos.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Item da lista de conversas do Alfredo (barra de histórico).
 * {@code active} marca a conversa aberta no momento.
 */
public record ChatSummaryDto(
        UUID id,
        String title,
        Instant createdAt,
        boolean active
) {
}
