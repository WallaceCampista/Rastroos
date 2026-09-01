package com.rastroos.web.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rastroos.domain.service.AuthService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.form.ChangePasswordForm;
import com.rastroos.web.form.ProfileForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Telas relacionadas ao perfil do usuário autenticado. Por enquanto, só
 * troca de senha. Edição de nome/email entra na Etapa 14.
 */
@Controller
@RequestMapping("/app/profile")
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final CurrentUser currentUser;
    private final AuthService auth;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ProfileController(CurrentUser currentUser, AuthService auth,
                             UserDetailsService userDetailsService) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping
    public String profilePage(Model model) {
        ProfileForm form = new ProfileForm();
        currentUser.get().ifPresent(u -> {
            form.setName(u.getName());
            model.addAttribute("profileEmail", u.getEmail());
        });
        model.addAttribute("profileForm", form);
        return "app/profile";
    }

    @PostMapping
    public String profileSubmit(@Valid @ModelAttribute("profileForm") ProfileForm form,
                                BindingResult binding, Model model,
                                HttpServletRequest request, HttpServletResponse response) {
        if (binding.hasErrors()) {
            currentUser.get().ifPresent(u -> model.addAttribute("profileEmail", u.getEmail()));
            return "app/profile";
        }
        auth.updateName(currentUser.requireId(), form.getName());
        refreshPrincipal(request, response);
        return "redirect:/app/dashboard?profileSaved";
    }

    /**
     * Recarrega o principal autenticado a partir do banco e o regrava na sessão,
     * para que nome/iniciais exibidos (topbar, sidebar) reflitam a alteração sem
     * exigir novo login. O username (email) não muda aqui, só o nome de exibição.
     */
    private void refreshPrincipal(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null) {
            return;
        }
        UserDetails fresh = userDetailsService.loadUserByUsername(current.getName());
        UsernamePasswordAuthenticationToken refreshed =
                new UsernamePasswordAuthenticationToken(fresh, current.getCredentials(), fresh.getAuthorities());
        refreshed.setDetails(current.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @GetMapping("/change-password")
    public String changePasswordPage(Model model) {
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        return "app/change-password";
    }

    @PostMapping("/change-password")
    public String changePasswordSubmit(@Valid @ModelAttribute("changePasswordForm")
                                       ChangePasswordForm form,
                                       BindingResult binding) {
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            binding.rejectValue("newPasswordConfirm", "password.mismatch");
        }
        if (binding.hasErrors()) return "app/change-password";

        var errs = auth.changePassword(currentUser.requireId(),
                                       form.getCurrentPassword(),
                                       form.getNewPassword());
        if (!errs.isEmpty()) {
            errs.forEach(err -> {
                if ("password.currentWrong".equals(err)) {
                    binding.rejectValue("currentPassword", err);
                } else {
                    binding.rejectValue("newPassword", err);
                }
            });
            return "app/change-password";
        }
        return "redirect:/?openLogin=login&passwordChanged";
    }
}
