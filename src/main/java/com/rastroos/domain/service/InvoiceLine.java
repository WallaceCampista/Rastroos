package com.rastroos.domain.service;

import java.math.BigDecimal;

/**
 * Uma linha lida da fatura.
 *
 * @param dateLabel    a data exatamente como impressa (ex.: "12/09"), só para a
 *                     pessoa reconhecer o item — nunca vira data de lançamento
 * @param description  estabelecimento, já sem o marcador de parcela
 * @param installment  número da parcela cobrada nesta fatura, ou {@code null}
 * @param installments total de parcelas da compra, ou {@code null}
 */
public record InvoiceLine(
        String dateLabel,
        String description,
        BigDecimal amount,
        Short installment,
        Short installments,
        InvoiceLineType type,
        String categoryId
) {
}
