package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.rastroos.domain.entity.enums.InvestmentKind;

/**
 * Linha exibida em /app/investments e no REST. Para cofrinhos
 * ({@link InvestmentKind#PIGGY}), {@code goal} e {@code progressPercent}
 * costumam estar preenchidos. Para a carteira, {@code rateLabel} e
 * {@code monthlyReturn} são tipicamente preenchidos.
 */
public record InvestmentDto(
        UUID id,
        String name,
        InvestmentKind kind,
        BigDecimal amount,
        BigDecimal goal,
        Integer progressPercent,
        String rateLabel,
        BigDecimal monthlyReturn,
        String colorHex,
        String iconText
) {
    public boolean isPiggy() {
        return kind == InvestmentKind.PIGGY;
    }
}
