package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O que o modelo leu de uma fatura de cartão. Campo {@code null} = ilegível no
 * documento — jamais um palpite.
 *
 * @param dueDate vencimento impresso na fatura
 * @param total   total a pagar impresso na fatura
 * @param last4   4 últimos dígitos do cartão impressos na fatura
 */
public record InvoiceReading(
        LocalDate dueDate,
        BigDecimal total,
        String last4,
        List<InvoiceLine> lines
) {
}
