package com.rastroos.web.dto;

/**
 * Contagens por bucket para os chips de filtro da tela de gastos
 * (Todos / Em aberto / Pago / Fixo / Pontual), no contexto do mês.
 */
public record TransactionFilterCounts(
        long total,
        long paid,
        long unpaid,
        long fixed,
        long oneOff
) {
    public static TransactionFilterCounts empty() {
        return new TransactionFilterCounts(0, 0, 0, 0, 0);
    }
}
