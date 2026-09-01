package com.rastroos.web.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rastroos.domain.repository.UserRepository;
import com.rastroos.security.CurrentUser;

import jakarta.servlet.http.HttpSession;

/**
 * "Ver como usuário" (admin): guarda na sessão o id do usuário cujos dados o
 * admin quer visualizar. {@link CurrentUser#effectiveUserId()} passa a usar
 * esse id em todas as telas, até o admin trocar/limpar a seleção.
 *
 * <p>Somente ADMIN. O id é validado no servidor (existe? não é acessor?), nunca
 * confiado cru — é a única entrada que muda o "dono dos dados" via request.
 */
@Controller
@RequestMapping("/app/view-as")
@PreAuthorize("hasRole('ADMIN')")
public class ViewAsController {

    private final UserRepository users;
    private final CurrentUser currentUser;

    public ViewAsController(UserRepository users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @PostMapping
    public String select(@RequestParam(value = "userId", required = false) String userId,
                         @RequestParam(value = "redirect", required = false) String redirect,
                         HttpSession session) {
        UUID me = currentUser.requireId();
        UUID target = parse(userId);

        if (target == null || target.equals(me) || !users.existsById(target)) {
            // "Ver como eu mesmo" / inválido → limpa o override
            session.removeAttribute(CurrentUser.VIEW_AS_SESSION_KEY);
        } else {
            session.setAttribute(CurrentUser.VIEW_AS_SESSION_KEY, target);
        }
        return "redirect:" + safeRedirect(redirect);
    }

    private static UUID parse(String s) {
        if (s == null || s.isBlank() || "self".equals(s)) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Anti open-redirect: só caminhos internos do app. */
    private static String safeRedirect(String redirect) {
        if (redirect != null && redirect.startsWith("/app/") && !redirect.startsWith("/app//")) {
            return redirect;
        }
        return "/app/dashboard";
    }
}
