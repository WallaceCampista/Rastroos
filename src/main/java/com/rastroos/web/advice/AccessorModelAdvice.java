package com.rastroos.web.advice;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.rastroos.security.CurrentUser;

/**
 * Injeta em todas as telas ({@code com.rastroos.web.controller}) os atributos
 * do modo acessor, para a UI reagir sem lógica inline:
 * <ul>
 *   <li>{@code accessorMode} — a conta logada é um ACESSOR;</li>
 *   <li>{@code maskValues} — o titular ocultou os valores (renderizar '*');</li>
 *   <li>{@code accessTargetName} — nome do titular cujos dados estão sendo vistos.</li>
 * </ul>
 * Tudo vem do principal autenticado ({@link CurrentUser}) — sem consulta extra
 * ao banco por request.
 */
@ControllerAdvice(basePackages = "com.rastroos.web.controller")
public class AccessorModelAdvice {

    private final CurrentUser currentUser;

    public AccessorModelAdvice(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute
    public void accessorAttributes(Model model) {
        boolean accessor = currentUser.isAccessor();
        model.addAttribute("accessorMode", accessor);
        model.addAttribute("maskValues", currentUser.isMaskActive());
        if (accessor) {
            model.addAttribute("accessTargetName", currentUser.accessTargetName().orElse(null));
        }
    }
}
