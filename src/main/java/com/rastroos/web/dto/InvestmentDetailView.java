package com.rastroos.web.dto;

import java.util.List;

/** Detalhe de um investimento: o resumo + as movimentações (mais recentes primeiro). */
public record InvestmentDetailView(
        InvestmentDto investment,
        List<InvestmentMovementDto> movements
) {
}
