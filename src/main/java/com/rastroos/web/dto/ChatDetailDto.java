package com.rastroos.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Conversa aberta com suas mensagens em ordem cronológica.
 */
public record ChatDetailDto(
        UUID id,
        String title,
        List<ChatMessageDto> messages
) {
}
