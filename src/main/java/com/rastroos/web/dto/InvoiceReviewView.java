package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Conferência de uma fatura antes de lançar: o que foi lido, o que já estava no
 * sistema e o que será criado. Com {@code error} preenchido (chave i18n), a
 * leitura falhou e só o aviso é exibido.
 *
 * @param items    linhas que podem virar lançamento (compras e encargos)
 * @param ignored  créditos e pagamentos: exibidos, nunca importados
 * @param dueDateRead vencimento veio do documento (senão a tela pede para conferir)
 */
public record InvoiceReviewView(
        UUID accountId,
        String accountName,
        String accountColorHex,
        String accountIconText,
        String accountLast4,
        String error,
        LocalDate dueDate,
        boolean dueDateRead,
        BigDecimal totalRead,
        String last4Read,
        List<InvoiceReviewItem> items,
        List<InvoiceReviewItem> ignored,
        List<CategoryOptionDto> categories
) {
    public static InvoiceReviewView failure(AccountSummaryDto account, String errorKey) {
        return new InvoiceReviewView(account.id(), account.name(), account.colorHex(), account.iconText(),
                account.last4(), errorKey, null, false, null, null, List.of(), List.of(), List.of());
    }

    public boolean hasError() {
        return error != null;
    }

    /** A fatura diz outro final de cartão: provável anexo no cartão errado. */
    public boolean last4Mismatch() {
        return accountLast4 != null && last4Read != null && !accountLast4.equals(last4Read);
    }

    public long count(String status) {
        return items.stream().filter(i -> i.status().equals(status)).count();
    }

    public long selectedCount() {
        return items.stream().filter(InvoiceReviewItem::selected).count();
    }

    public BigDecimal selectedSum() {
        return items.stream().filter(InvoiceReviewItem::selected)
                .map(InvoiceReviewItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Soma de tudo que foi lido como compra ou encargo, lançado ou não. */
    public BigDecimal itemsSum() {
        return items.stream().map(InvoiceReviewItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal ignoredSum() {
        return ignored.stream().map(InvoiceReviewItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
