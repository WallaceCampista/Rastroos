package com.rastroos.web.dto;

import java.time.Instant;

import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;

/**
 * Linha da listagem de chamados. {@code requesterEmail} só é preenchido na
 * visão do admin (na visão do próprio usuário fica {@code null}, pois todos
 * os chamados são dele).
 */
public record TicketSummaryDto(
        String id,
        SupportTicketCategory category,
        String title,
        SupportTicketPriority priority,
        SupportTicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        long commentCount,
        String requesterEmail
) {
}
