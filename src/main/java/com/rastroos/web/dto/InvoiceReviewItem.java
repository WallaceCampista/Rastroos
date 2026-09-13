package com.rastroos.web.dto;

import java.math.BigDecimal;

import com.rastroos.domain.service.InvoiceLineType;

/**
 * Uma linha da fatura na conferência, já cruzada com o que está lançado.
 *
 * @param index           posição no formulário ({@code items[index]})
 * @param status          {@code new} | {@code existing} | {@code adjust} | {@code duplicate} | {@code ignored}
 * @param selectable      a pessoa pode marcar/desmarcar (já lançado e ignorado não podem)
 * @param matchDescription o lançamento existente que casou, quando houver
 * @param matchAmount     valor desse lançamento
 * @param futureToCreate  parcelas seguintes que serão criadas se a linha for lançada
 * @param futureExisting  parcelas seguintes que já estavam lançadas (não serão recriadas)
 */
public record InvoiceReviewItem(
        int index,
        boolean selected,
        String dateLabel,
        String description,
        BigDecimal amount,
        Short installment,
        Short installments,
        InvoiceLineType type,
        String categoryId,
        String status,
        boolean selectable,
        String matchDescription,
        BigDecimal matchAmount,
        int futureToCreate,
        int futureExisting
) {
    public boolean isInstallment() {
        return installment != null && installments != null && installments > 1;
    }

    public String installmentLabel() {
        return isInstallment() ? installment + "/" + installments : null;
    }
}
