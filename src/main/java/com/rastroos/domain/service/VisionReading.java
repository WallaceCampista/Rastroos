package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que o modelo de visão conseguiu ler do documento. Campo {@code null} =
 * ilegível — jamais um palpite.
 */
public record VisionReading(
        String description,
        BigDecimal amount,
        LocalDate date,
        String last4
) {
    /** {@code true} quando nada útil foi lido (vale cair no modo demonstração). */
    public boolean isEmpty() {
        return description == null && amount == null && date == null && last4 == null;
    }
}
