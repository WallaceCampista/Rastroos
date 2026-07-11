package com.rastroos.web.dto;

import java.time.Instant;
import java.util.List;

import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;

/**
 * Detalhe de um chamado com seu thread de comentários.
 *
 * @param admin         se o espectador é admin (mostra dono + controle de status)
 * @param canComment    se ainda pode comentar (chamado não fechado/cancelado)
 * @param canCancel     se o dono pode cancelar (chamado aberto/em andamento)
 * @param requesterEmail dono do chamado (relevante na visão admin)
 */
public record TicketDetailView(
        String id,
        SupportTicketCategory category,
        String title,
        String description,
        SupportTicketPriority priority,
        SupportTicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        String requesterEmail,
        List<TicketCommentDto> comments,
        boolean admin,
        boolean canComment,
        boolean canCancel
) {
}
