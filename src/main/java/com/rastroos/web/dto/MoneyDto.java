package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pequeno helper para conversão de centavos (modelo interno) para
 * {@link BigDecimal} (apresentação). NUNCA usar {@code double} para dinheiro.
 */
public final class MoneyDto {

    private MoneyDto() {
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
