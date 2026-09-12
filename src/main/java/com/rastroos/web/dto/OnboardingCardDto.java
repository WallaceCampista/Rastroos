package com.rastroos.web.dto;

import java.util.UUID;

import com.rastroos.domain.entity.enums.AccountKind;

/**
 * Cartão já cadastrado, como aparece na lista do wizard de boas-vindas.
 * Enxuto de propósito: ali não há mês nem fatura, só o cadastro.
 */
public record OnboardingCardDto(
        UUID id,
        String name,
        AccountKind kind,
        String colorHex,
        String last4,
        Short closeDay,
        Short dueDay
) {
    public boolean credit() {
        return kind == AccountKind.CARD;
    }
}
