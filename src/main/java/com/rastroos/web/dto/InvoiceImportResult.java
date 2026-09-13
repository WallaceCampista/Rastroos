package com.rastroos.web.dto;

import java.time.YearMonth;

/**
 * O que a importação gravou.
 *
 * @param created       lançamentos criados na fatura importada
 * @param futureCreated parcelas criadas nas faturas seguintes
 * @param adjusted      parcelas já lançadas que tiveram o valor corrigido pela fatura
 * @param month         mês da fatura importada
 */
public record InvoiceImportResult(int created, int futureCreated, int adjusted, YearMonth month) {

    public boolean nothingChanged() {
        return created == 0 && futureCreated == 0 && adjusted == 0;
    }
}
