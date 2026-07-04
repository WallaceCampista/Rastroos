package com.rastroos.web.controller;

import org.springframework.security.access.prepost.PreAuthorize;
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

    public ProfileController(CurrentUser currentUser, AuthService auth) {
        this.currentUser = currentUser;
        this.auth = auth;
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
        return "redirect:/auth/login?passwordChanged";
    }
}
