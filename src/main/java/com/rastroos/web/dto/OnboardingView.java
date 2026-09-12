package com.rastroos.web.dto;

import java.util.List;

import com.rastroos.domain.entity.enums.UserTheme;

/**
 * Estado do wizard de boas-vindas: o que o usuário já preencheu até agora.
 * Cada passo é renderizado a partir daqui, então voltar um passo mostra o
 * que foi salvo, não um formulário em branco.
 */
public record OnboardingView(
        String name,
        String email,
        UserTheme theme,
        short paletteIndex,
        List<OnboardingCardDto> cards,
        List<IncomeSourceDto> sources
) {
}
