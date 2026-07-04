package com.rastroos.web.dto;

import java.util.List;

/**
 * Snapshot da tela "Cartões &amp; Contas": cartões e contas separados,
 * cada lista já com agregados do mês.
 */
public record AccountsView(
        List<AccountSummaryDto> cards,
        List<AccountSummaryDto> bills,
        List<AccountSummaryDto> recurrent
) {
}
