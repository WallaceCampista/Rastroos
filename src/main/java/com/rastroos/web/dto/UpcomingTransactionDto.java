package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha de "próximos vencimentos" no dashboard.
 */
public record UpcomingTransactionDto(
        UUID id,
        String description,
        UUID accountId,
        String accountName,
        String categoryId,
        BigDecimal amount,
        LocalDate dueDate,
        boolean fixed
) {
}
