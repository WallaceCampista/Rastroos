package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Página paginada de transações + totais do período (filtrado) já em reais.
 * Usada tanto no Web (tela) quanto no REST (JSON).
 */
public record TransactionsPageView(
        List<TransactionDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal totalAmount,
        BigDecimal totalPaid
) {
    public BigDecimal totalUnpaid() {
        return totalAmount.subtract(totalPaid);
    }
}
