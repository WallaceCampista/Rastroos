package com.rastroos.web.advice;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.rastroos.security.CurrentUser;

/**
 * Preferências visuais do usuário logado ({@code userTheme}, {@code userPalette},
 * {@code userDensity}) e o estado do wizard de boas-vindas
 * ({@code onboardingPending}), para o {@code <body>} do layout.
 *
 * <p>Renderizar o tema no servidor evita o "pisca" claro→escuro do primeiro
 * frame. O {@code theme.js} ainda pode sobrepor com a escolha local
 * (localStorage) — o valor do banco é o ponto de partida, inclusive num
 * dispositivo novo, onde não há nada guardado.
 *
 * <p>Lê tudo do principal em sessão, sem ida ao banco por request; quem
 * altera essas preferências recarrega o principal
 * ({@code PrincipalRefresher}).
 */
@ControllerAdvice
public class UserPreferencesAdvice {

    private final CurrentUser currentUser;

    public UserPreferencesAdvice(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute
    public void addPreferences(Model model) {
        currentUser.get().ifPresent(user -> {
            if (user.getTheme() != null) {
                model.addAttribute("userTheme", user.getTheme().name());
            }
            if (user.getDensity() != null) {
                model.addAttribute("userDensity", user.getDensity().name());
            }
            model.addAttribute("userPalette", user.getPaletteIndex());
            // Acessor opera dados de outra pessoa: não é a conta dele que está
            // sendo configurada, então o wizard não faz sentido ali.
            model.addAttribute("onboardingPending",
                    user.isOnboardingPending() && !user.isAccessor());
        });
    }
}
