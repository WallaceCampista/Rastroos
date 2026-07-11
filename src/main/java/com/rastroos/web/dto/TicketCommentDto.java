package com.rastroos.web.dto;

import java.time.Instant;

import com.rastroos.domain.entity.enums.UserRole;

/**
 * Um comentário no thread de um chamado. {@code mine} indica se o autor é o
 * usuário corrente (para alinhamento/destaque na UI).
 */
public record TicketCommentDto(
        UserRole authorRole,
        String authorEmail,
        String body,
        Instant createdAt,
        boolean mine
) {
}
