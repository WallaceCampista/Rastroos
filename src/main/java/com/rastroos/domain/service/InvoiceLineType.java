package com.rastroos.domain.service;

/** Natureza de uma linha da fatura, como o modelo a classificou. */
public enum InvoiceLineType {

    /** Compra, parcela ou assinatura. */
    PURCHASE,

    /** IOF, anuidade, juros, multa, tarifa. */
    FEE,

    /** Estorno, crédito, devolução. */
    CREDIT,

    /** Pagamento da fatura anterior. */
    PAYMENT;

    /**
     * Só compra e encargo viram lançamento. Crédito e pagamento entram com
     * sinal contrário, e {@code transactions.amount_cents} só aceita valor
     * positivo — eles aparecem na conferência, mas não são importados.
     */
    public boolean importable() {
        return this == PURCHASE || this == FEE;
    }

    /** Valor desconhecido vira compra: fica visível (e desmarcável) na conferência, em vez de sumir. */
    public static InvoiceLineType parse(String raw) {
        if (raw != null) {
            for (InvoiceLineType type : values()) {
                if (type.name().equalsIgnoreCase(raw.trim())) {
                    return type;
                }
            }
        }
        return PURCHASE;
    }
}
