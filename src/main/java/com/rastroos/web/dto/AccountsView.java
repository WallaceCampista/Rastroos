package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot da tela "Cartões &amp; Contas": cartões e contas separados,
 * cada lista já com agregados do mês. Os KPIs do topo (igual ao protótipo)
 * somam apenas os lançamentos <em>fixos</em> do mês; {@code fixedCount} é a
 * quantidade de contas fixas (não-cartão).
 */
public record AccountsView(
        List<AccountSummaryDto> cards,
        List<AccountSummaryDto> bills,
        List<AccountSummaryDto> recurrent,
        BigDecimal totalFixed,
        BigDecimal paidFixed,
        BigDecimal remainingFixed,
        int fixedCount
) {

    public int count() {
        return cards.size() + bills.size() + recurrent.size();
    }
}
