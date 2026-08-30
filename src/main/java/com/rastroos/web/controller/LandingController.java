package com.rastroos.web.controller;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LandingController {

    private static final Set<String> ALLOWED_DRAWER_VIEWS = Set.of("login", "signup", "forgot");

    @GetMapping({"/", "/landing"})
    public String index(@RequestParam(name = "openLogin", required = false) String openLogin,
                        @RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "reset", required = false) String reset,
                        @RequestParam(name = "logout", required = false) String logout,
                        @RequestParam(name = "passwordChanged", required = false) String passwordChanged,
                        @RequestParam(name = "email", required = false) String email,
                        Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
        if (authenticated) {
            return "redirect:/app/dashboard";
        }

        // O login mora no drawer da landing. Falha/lockout/reset reabrem o drawer
        // de login com a mensagem correspondente.
        String view = openLogin;
        if (view == null && (error != null || reset != null || passwordChanged != null)) {
            view = "login";
        }
        if (view != null && ALLOWED_DRAWER_VIEWS.contains(view)) {
            model.addAttribute("openLogin", view);
        }
        if ("locked".equals(error)) {
            model.addAttribute("loginError", "locked");
        } else if (error != null) {
            model.addAttribute("loginError", "invalid");
        }
        if (reset != null) {
            model.addAttribute("resetOk", true);
        }
        if (passwordChanged != null) {
            model.addAttribute("passwordChanged", true);
        }
        if (logout != null) {
            model.addAttribute("loggedOut", true);
        }
        if (email != null && !email.isBlank()) {
            model.addAttribute("prefillEmail", email);
        }
        return "landing";
    }
}
