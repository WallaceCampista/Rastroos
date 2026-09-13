package com.rastroos.web.advice;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.rastroos.domain.service.AiProviderSetting;
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
 *
 * <p>Também expõe, <b>só para administradores</b>, o motor de IA ativo
 * ({@code aiProvider}) — é o que o seletor do menu do usuário mostra.
 */
@ControllerAdvice
public class UserPreferencesAdvice {

    private final CurrentUser currentUser;
    /** ObjectProvider para o advice continuar construtível em fatias @WebMvcTest. */
    private final ObjectProvider<AiProviderSetting> aiProvider;

    public UserPreferencesAdvice(CurrentUser currentUser,
                                 ObjectProvider<AiProviderSetting> aiProvider) {
        this.currentUser = currentUser;
        this.aiProvider = aiProvider;
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
        addAiProvider(model);
    }

    /** Motor de IA ativo — só o administrador vê e troca. */
    private void addAiProvider(Model model) {
        if (!currentUser.isAdmin()) {
            return;
        }
        AiProviderSetting setting = aiProvider.getIfAvailable();
        if (setting == null) {
            return;
        }
        model.addAttribute("aiProvider", setting.current());
        model.addAttribute("aiProviderLocked", setting.locked());
        model.addAttribute("aiProviderConfigured",
                setting.known().stream().filter(setting::configured).toList());
    }
}
