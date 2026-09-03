package com.rastroos.web.advice;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.rastroos.security.CurrentUser;

/**
 * Expõe a identidade do usuário logado para os templates (avatar da sidebar e
 * do menu da topbar): {@code userDisplayName} e {@code userInitial}. A inicial
 * é derivada do nome (ou do e-mail, se não houver nome), espelhando o cálculo
 * de iniciais do protótipo.
 *
 * <p>Também expõe {@code sideCollapsed}: o estado recolhido da sidebar,
 * persistido num cookie leve de UI. Renderizar esse estado no servidor evita o
 * "pisca" (expandido → recolhido) que aparecia quando o JS recolhia só depois
 * do carregamento.
 */
@ControllerAdvice
public class UserModelAdvice {

    private final CurrentUser currentUser;

    public UserModelAdvice(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute("sideCollapsed")
    public boolean sideCollapsed(
            @CookieValue(name = "sideCollapsed", defaultValue = "false") String collapsed) {
        // Parse leniente: só "true" recolhe. Um cookie malformado nunca vira 400.
        return "true".equalsIgnoreCase(collapsed);
    }

    @ModelAttribute
    public void addUserIdentity(Model model) {
        currentUser.get().ifPresent(user -> {
            String name = user.getName();
            String email = user.getEmail();
            String display = (name != null && !name.isBlank()) ? name.trim() : email;
            model.addAttribute("userDisplayName", display);
            model.addAttribute("userInitial", initials(display));
        });
    }

    private String initials(String source) {
        if (source == null || source.isBlank()) {
            return "·";
        }
        StringBuilder sb = new StringBuilder(2);
        for (String part : source.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (sb.length() == 2) {
                    break;
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "·";
    }
}
