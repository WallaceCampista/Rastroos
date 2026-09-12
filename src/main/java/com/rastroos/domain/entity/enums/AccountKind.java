package com.rastroos.domain.entity.enums;

public enum AccountKind {
    /** Cartão de crédito: tem fatura, dia de fechamento e dia de vencimento. */
    CARD,
    /** Cartão de débito: o gasto sai na hora, sem fatura para fechar. */
    DEBIT,
    BILL,
    RECURRENT;

    /** {@code true} para os tipos que representam um cartão físico (crédito ou débito). */
    public boolean isCard() {
        return this == CARD || this == DEBIT;
    }
}
