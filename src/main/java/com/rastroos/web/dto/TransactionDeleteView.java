package com.rastroos.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Modelo do modal "excluir lançamento". Parcelas de uma compra e meses de um
 * gasto fixo formam uma série; só com série há o que escolher.
 *
 * @param seriesTotal    lançamentos da série (1 quando não há série)
 * @param seriesFromHere lançamentos da série deste vencimento em diante, incluindo este
 */
public record TransactionDeleteView(
        UUID id,
        String description,
        UUID accountId,
        LocalDate dueDate,
        boolean fixed,
        Short installmentCurrent,
        Short installmentTotal,
        long seriesTotal,
        long seriesFromHere
) {
    public boolean hasSeries() {
        return seriesTotal > 1;
    }

    public boolean installment() {
        return installmentTotal != null && installmentTotal > 1;
    }

    public String installmentLabel() {
        return installment() ? installmentCurrent + "/" + installmentTotal : null;
    }

    /**
     * "Desta em diante" só aparece quando é diferente das outras duas opções:
     * precisa haver algo depois deste lançamento (senão é "só este") e algo
     * antes dele (senão é "todos").
     */
    public boolean offersFromHere() {
        return seriesFromHere > 1 && seriesFromHere < seriesTotal;
    }
}
