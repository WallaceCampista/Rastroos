package com.rastroos.web.dto;

import java.time.Instant;

import com.rastroos.domain.entity.enums.ChatMessageRole;

/**
 * Mensagem de uma conversa. {@code assistant} distingue as respostas do
 * Alfredo das mensagens do usuário (alinhamento/estilo na bolha).
 */
public record ChatMessageDto(
        ChatMessageRole role,
        String content,
        Instant createdAt,
        boolean assistant
) {
}
