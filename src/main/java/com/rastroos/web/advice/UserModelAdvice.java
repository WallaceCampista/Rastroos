package com.rastroos.web.advice;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.rastroos.security.CurrentUser;

/**
 * Expõe a identidade do usuário logado para os templates (avatar da sidebar e
 * do menu da topbar): {@code userDisplayName} e {@code userInitial}. A inicial
 * é derivada do nome (ou do e-mail, se não houver nome), espelhando o cálculo
 * de iniciais do protótipo.
 */
@ControllerAdvice
public class UserModelAdvice {

    private final CurrentUser currentUser;

    public UserModelAdvice(CurrentUser currentUser) {
        this.currentUser = currentUser;
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
