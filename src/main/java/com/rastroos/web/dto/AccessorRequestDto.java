package com.rastroos.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.rastroos.domain.entity.enums.AccessorRequestStatus;

/**
 * Solicitação de acessor para exibição. {@code requesterName}/{@code requesterEmail}
 * só são preenchidos na fila do admin (na visão do titular são redundantes).
 */
public record AccessorRequestDto(
        UUID id,
        UUID requesterUserId,
        String requesterName,
        String requesterEmail,
        String accessorName,
        String accessorEmail,
        String note,
        AccessorRequestStatus status,
        Instant createdAt,
        Instant resolvedAt,
        UUID createdUserId
) {
}
