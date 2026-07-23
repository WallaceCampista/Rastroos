package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.rastroos.domain.entity.enums.AccountKind;

/**
 * Resumo de uma conta para listagem (telas Dashboard e Cards):
 * inclui agregados do mês — total, pago, % pago e contagem de lançamentos.
 */
public record AccountSummaryDto(
        UUID id,
        String name,
        AccountKind kind,
        String colorHex,
        String iconText,
        Short closeDay,
        Short dueDay,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal remaining,
        int paidPercent,
        long entriesCount,
        /** {@code paid} | {@code overdue} | {@code soon} | {@code open} | {@code none} */
        String status
) {

    public boolean isFullyPaid() {
        return total.signum() > 0 && remaining.signum() <= 0;
    }
}
