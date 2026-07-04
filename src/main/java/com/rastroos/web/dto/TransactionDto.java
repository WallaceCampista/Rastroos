package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha exibida na tela /app/expenses (e no REST /api/v1/transactions).
 * Inclui descrições resolvidas de conta/categoria para evitar joins na view.
 */
public record TransactionDto(
        UUID id,
        String description,
        UUID accountId,
        String accountName,
        String accountColorHex,
        String categoryId,
        String categoryName,
        String categoryColorHex,
        BigDecimal amount,
        LocalDate dueDate,
        boolean fixed,
        boolean paid,
        Instant paidAt,
        Short installmentCurrent,
        Short installmentTotal
) {
    public boolean isInstallment() {
        return installmentTotal != null && installmentTotal > 1;
    }

    public String installmentLabel() {
        if (!isInstallment()) return null;
        return installmentCurrent + "/" + installmentTotal;
    }
}
