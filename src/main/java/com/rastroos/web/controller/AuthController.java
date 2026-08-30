package com.rastroos.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.service.AuthService;
import com.rastroos.domain.service.AuthService.SignupOutcome;
import com.rastroos.web.form.ForgotForm;
import com.rastroos.web.form.ResetForm;
import com.rastroos.web.form.SignupForm;
import com.rastroos.web.form.VerifyForm;

import jakarta.validation.Valid;

/**
 * Páginas e POSTs dos fluxos de autenticação (signup, verify, forgot, reset).
 * O login NÃO tem página própria: mora no drawer da landing ({@code /}), que
 * faz POST em /auth/login (processado pelo Spring Security via formLogin).
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    // ── SIGNUP ──────────────────────────────────────────────────────────────
    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signupSubmit(@Valid @ModelAttribute("signupForm") SignupForm form,
                               BindingResult binding,
                               RedirectAttributes flash) {
        if (!form.getPassword().equals(form.getPasswordConfirm())) {
            binding.rejectValue("passwordConfirm", "password.mismatch");
        }
        if (binding.hasErrors()) return "auth/signup";

        SignupOutcome outcome = auth.signup(form.getName(), form.getEmail(), form.getPassword());
        if (!outcome.passwordErrors().isEmpty()) {
            outcome.passwordErrors().forEach(err -> binding.rejectValue("password", err));
            return "auth/signup";
        }
        flash.addAttribute("email", form.getEmail());
        return "redirect:/auth/verify";
    }

    // ── VERIFY ──────────────────────────────────────────────────────────────
    @GetMapping("/verify")
    public String verifyPage(@RequestParam(required = false) String email, Model model) {
        VerifyForm form = new VerifyForm();
        form.setEmail(email);
        model.addAttribute("verifyForm", form);
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String verifySubmit(@Valid @ModelAttribute("verifyForm") VerifyForm form,
                               BindingResult binding,
                               RedirectAttributes flash) {
        if (binding.hasErrors()) return "auth/verify";
        boolean ok = auth.verifyEmail(form.getEmail(), form.getCode());
        if (!ok) {
            binding.rejectValue("code", "verify.invalid");
            return "auth/verify";
        }
        return "redirect:/auth/signup-success";
    }

    @GetMapping("/signup-success")
    public String signupSuccess() { return "auth/signup-success"; }

    // ── FORGOT ──────────────────────────────────────────────────────────────
    @GetMapping("/forgot")
    public String forgotPage(Model model) {
        model.addAttribute("forgotForm", new ForgotForm());
        return "auth/forgot";
    }

    @PostMapping("/forgot")
    public String forgotSubmit(@Valid @ModelAttribute("forgotForm") ForgotForm form,
                               BindingResult binding,
                               RedirectAttributes flash) {
        if (binding.hasErrors()) return "auth/forgot";
        auth.requestPasswordReset(form.getEmail());
        flash.addAttribute("email", form.getEmail());
        return "redirect:/auth/reset";
    }

    // ── RESET ───────────────────────────────────────────────────────────────
    @GetMapping("/reset")
    public String resetPage(@RequestParam(required = false) String email, Model model) {
        ResetForm form = new ResetForm();
        form.setEmail(email);
        model.addAttribute("resetForm", form);
        return "auth/reset";
    }

    @PostMapping("/reset")
    public String resetSubmit(@Valid @ModelAttribute("resetForm") ResetForm form,
                              BindingResult binding) {
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            binding.rejectValue("newPasswordConfirm", "password.mismatch");
        }
        if (binding.hasErrors()) return "auth/reset";

        var errs = auth.confirmPasswordReset(form.getEmail(), form.getCode(), form.getNewPassword());
        if (!errs.isEmpty()) {
            errs.forEach(err -> {
                if ("reset.invalidCode".equals(err)) {
                    binding.rejectValue("code", err);
                } else {
                    binding.rejectValue("newPassword", err);
                }
            });
            return "auth/reset";
        }
        return "redirect:/?openLogin=login&reset=ok";
    }
}
