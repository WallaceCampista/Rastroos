package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Página paginada de receitas do mês. Usada na tela /app/income e no REST.
 *
 * @param totalAmount    tudo que está lançado no mês (a previsão)
 * @param receivedAmount a parcela já confirmada como recebida
 */
public record IncomesPageView(
        List<IncomeDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal totalAmount,
        BigDecimal receivedAmount
) {
    /** O que ainda falta cair na conta neste mês. */
    public BigDecimal pendingAmount() {
        return totalAmount.subtract(receivedAmount).max(BigDecimal.ZERO);
    }
}
