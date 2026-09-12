package com.rastroos.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Modelo do modal "excluir receita", que atende dois pontos de entrada:
 * a lixeira de um lançamento e a lixeira de uma receita fixa.
 *
 * @param incomeId  lançamento clicado; {@code null} quando veio da receita fixa
 * @param label     o que aparece no título (origem do lançamento ou nome da empresa)
 * @param source    a receita recorrente por trás; {@code null} num lançamento avulso
 * @param total     recebimentos gerados pela fonte
 * @param fromMonth recebimentos da fonte no mês de corte ou depois dele
 */
public record IncomeDeleteView(
        UUID incomeId,
        String label,
        LocalDate incomeDate,
        IncomeSourceDto source,
        String period,
        String monthLabel,
        long total,
        long fromMonth
) {
    public boolean recurring() {
        return source != null;
    }
}
