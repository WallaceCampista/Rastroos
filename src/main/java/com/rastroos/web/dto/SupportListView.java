package com.rastroos.web.dto;

import java.util.List;

/**
 * Página da tela /app/support: chamados + KPIs por status. Na visão do usuário
 * comum os contadores são só dos chamados dele; na visão admin, globais.
 */
public record SupportListView(
        List<TicketSummaryDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long openCount,
        long inProgressCount,
        long doneCount,
        boolean admin
) {
}
