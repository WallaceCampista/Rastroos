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
                        Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
        if (authenticated) {
            return "redirect:/app/dashboard";
        }

        if (openLogin != null && ALLOWED_DRAWER_VIEWS.contains(openLogin)) {
            model.addAttribute("openLogin", openLogin);
        }
        return "landing";
    }
}
