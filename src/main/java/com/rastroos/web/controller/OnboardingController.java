package com.rastroos.web.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.service.AccountService;
import com.rastroos.domain.service.IncomeSourceService;
import com.rastroos.domain.service.OnboardingService;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.PrincipalRefresher;
import com.rastroos.web.form.AccountForm;
import com.rastroos.web.form.IncomeSourceForm;
import com.rastroos.web.form.OnboardingAppearanceForm;
import com.rastroos.web.form.OnboardingProfileForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Wizard de boas-vindas do primeiro acesso, aberto como modal por cima da
 * tela atual ({@code onboarding.js}) e navegável como página cheia sem JS.
 *
 * <p>Cada passo é um POST que devolve o HTML do passo seguinte (200), então a
 * validação continua sendo do servidor. "Concluir" e "Pular" redirecionam —
 * é o sinal que o JS usa para fechar o modal e recarregar a tela.
 *
 * <p>Opera sempre sobre {@code requireId()}, nunca sobre o dono-dos-dados
 * efetivo: o wizard configura a <em>própria</em> conta de quem está logado.
 * Contas ACESSOR ficam de fora — elas operam dados de outra pessoa.
 */
@Controller
@RequestMapping("/app/onboarding")
@PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
public class OnboardingController {

    static final String STEP_PROFILE = "profile";
    static final String STEP_APPEARANCE = "appearance";
    static final String STEP_CARDS = "cards";
    static final String STEP_INCOME = "income";
    private static final Set<String> STEPS =
            Set.of(STEP_PROFILE, STEP_APPEARANCE, STEP_CARDS, STEP_INCOME);

    private static final String VIEW = "app/onboarding";
    private static final String SELF = "redirect:/app/onboarding?step=";

    private final CurrentUser currentUser;
    private final OnboardingService onboarding;
    private final AccountService accounts;
    private final IncomeSourceService incomeSources;
    private final PrincipalRefresher principalRefresher;

    public OnboardingController(CurrentUser currentUser,
                                OnboardingService onboarding,
                                AccountService accounts,
                                IncomeSourceService incomeSources,
                                PrincipalRefresher principalRefresher) {
        this.currentUser = currentUser;
        this.onboarding = onboarding;
        this.accounts = accounts;
        this.incomeSources = incomeSources;
        this.principalRefresher = principalRefresher;
    }

    @GetMapping
    public String open(@RequestParam(value = "step", required = false) String step, Model model) {
        UUID userId = currentUser.requireId();
        prepare(model, userId, normalize(step));
        return VIEW;
    }

    // ── Passo 1: perfil ──────────────────────────────────────────────────

    @PostMapping("/profile")
    public String saveProfile(@Valid @ModelAttribute("profileForm") OnboardingProfileForm form,
                              BindingResult binding, Model model,
                              HttpServletRequest request, HttpServletResponse response) {
        UUID userId = currentUser.requireId();
        if (!binding.hasErrors()) {
            List<String> errs = onboarding.saveProfile(userId, form);
            errs.forEach(err -> binding.rejectValue(passwordFieldFor(err), err));
        }
        if (binding.hasErrors()) {
            prepare(model, userId, STEP_PROFILE);
            return VIEW;
        }
        // Nome/tema/paleta vêm do principal em sessão: sem recarregar, a topbar
        // continuaria mostrando o nome antigo até o próximo login.
        principalRefresher.refresh(request, response);
        return SELF + STEP_APPEARANCE;
    }

    /** Em qual campo do formulário cada erro de senha deve aparecer. */
    private static String passwordFieldFor(String messageKey) {
        return switch (messageKey) {
            case "password.currentWrong" -> "currentPassword";
            case "password.mismatch" -> "newPasswordConfirm";
            default -> "newPassword";
        };
    }

    // ── Passo 2: aparência ───────────────────────────────────────────────

    @PostMapping("/appearance")
    public String saveAppearance(@Valid @ModelAttribute("appearanceForm") OnboardingAppearanceForm form,
                                 BindingResult binding, Model model,
                                 HttpServletRequest request, HttpServletResponse response) {
        UUID userId = currentUser.requireId();
        if (binding.hasErrors()) {
            prepare(model, userId, STEP_APPEARANCE);
            return VIEW;
        }
        onboarding.saveAppearance(userId, form.getTheme(), form.getPaletteIndex());
        principalRefresher.refresh(request, response);
        return SELF + STEP_CARDS;
    }

    // ── Passo 3: cartões ─────────────────────────────────────────────────

    @PostMapping("/cards")
    public String addCard(@Valid @ModelAttribute("accountForm") AccountForm form,
                          BindingResult binding, Model model, RedirectAttributes flash) {
        UUID userId = currentUser.requireId();
        if (!binding.hasErrors() && form.getKind() != null && !form.getKind().isCard()) {
            binding.rejectValue("kind", "onboarding.cardKindInvalid");
        }
        if (binding.hasErrors()) {
            prepare(model, userId, STEP_CARDS);
            return VIEW;
        }
        accounts.create(userId, form);
        // POST-redirect-GET: volta ao mesmo passo com o formulário limpo (e sem
        // reenviar o cartão se a pessoa der F5).
        flash.addFlashAttribute("ok", "onboarding.cardAdded");
        return SELF + STEP_CARDS;
    }

    // ── Passo 4: receita recorrente ──────────────────────────────────────

    @PostMapping("/income")
    public String addIncomeSource(@Valid @ModelAttribute("incomeSourceForm") IncomeSourceForm form,
                                  BindingResult binding, Model model, RedirectAttributes flash) {
        UUID userId = currentUser.requireId();
        if (!binding.hasErrors()) {
            try {
                incomeSources.create(userId, form);
            } catch (IllegalArgumentException e) {
                binding.rejectValue("incomeSource.duplicateName".equals(e.getMessage())
                        ? "name" : "amount", e.getMessage());
            }
        }
        if (binding.hasErrors()) {
            prepare(model, userId, STEP_INCOME);
            return VIEW;
        }
        flash.addFlashAttribute("ok", "onboarding.incomeAdded");
        return SELF + STEP_INCOME;
    }

    // ── Encerramento ─────────────────────────────────────────────────────

    /** Concluir e Pular fazem a mesma coisa: o wizard não volta a aparecer. */
    @PostMapping("/finish")
    public String finish(HttpServletRequest request, HttpServletResponse response) {
        onboarding.complete(currentUser.requireId());
        principalRefresher.refresh(request, response);
        return "redirect:/app/dashboard";
    }

    @PostMapping("/skip")
    public String skip(HttpServletRequest request, HttpServletResponse response) {
        return finish(request, response);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void prepare(Model model, UUID userId, String step) {
        var view = onboarding.state(userId);
        model.addAttribute("view", view);
        model.addAttribute("step", step);
        model.addAttribute("cardKinds", List.of(AccountKind.CARD, AccountKind.DEBIT));
        model.addAttribute("paletteCount", OnboardingService.PALETTE_COUNT);
        model.addAttribute("currentMonth", YearMonth.now());

        if (!model.containsAttribute("profileForm")) {
            OnboardingProfileForm f = new OnboardingProfileForm();
            f.setName(view.name());
            model.addAttribute("profileForm", f);
        }
        if (!model.containsAttribute("appearanceForm")) {
            OnboardingAppearanceForm f = new OnboardingAppearanceForm();
            f.setTheme(view.theme());
            f.setPaletteIndex(view.paletteIndex());
            model.addAttribute("appearanceForm", f);
        }
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", emptyCardForm());
        }
        if (!model.containsAttribute("incomeSourceForm")) {
            model.addAttribute("incomeSourceForm", emptyIncomeSourceForm());
        }
    }

    private static AccountForm emptyCardForm() {
        AccountForm f = new AccountForm();
        f.setKind(AccountKind.CARD);
        return f;
    }

    private static IncomeSourceForm emptyIncomeSourceForm() {
        IncomeSourceForm f = new IncomeSourceForm();
        f.setPayBusinessDay((short) 5);
        f.setStartMonth(YearMonth.now());
        return f;
    }

    private static String normalize(String step) {
        return (step != null && STEPS.contains(step)) ? step : STEP_PROFILE;
    }
}
