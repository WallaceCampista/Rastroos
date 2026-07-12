package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.service.AccountService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.form.AccountForm;

import jakarta.validation.Valid;

/**
 * Tela /app/cards: listagem (cartões + contas + recorrentes) e CRUD de contas.
 */
@Controller
@RequestMapping("/app/cards")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private final CurrentUser currentUser;
    private final AccountService accounts;
    private final Clock clock;

    public AccountController(CurrentUser currentUser, AccountService accounts, Clock clock) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(value = "ym", required = false) String ym, Model model) {
        YearMonth period = parseOrCurrent(ym);
        UUID userId = currentUser.requireEffectiveId();
        AccountsView view = accounts.listForMonth(userId, period);

        model.addAttribute("activeNav", "cards");
        model.addAttribute("period", period);
        model.addAttribute("view", view);
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", emptyForm());
        }
        model.addAttribute("kinds", AccountKind.values());
        return "app/cards";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("activeNav", "cards");
        model.addAttribute("accountForm", emptyForm());
        model.addAttribute("kinds", AccountKind.values());
        model.addAttribute("editing", false);
        return "app/account-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("accountForm") AccountForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            model.addAttribute("kinds", AccountKind.values());
            return "app/account-form";
        }
        accounts.create(currentUser.requireEffectiveId(), form);
        flash.addFlashAttribute("ok", "account.created");
        return "redirect:/app/cards";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        Account a = accounts.require(currentUser.requireEffectiveId(), id);
        AccountForm form = new AccountForm();
        form.setName(a.getName());
        form.setKind(a.getKind());
        form.setColorHex(a.getColorHex());
        form.setIconText(a.getIconText());
        form.setCloseDay(a.getCloseDay());
        form.setDueDay(a.getDueDay());
        form.setCategoryId(a.getCategoryId());
        form.setFixed(a.isFixed());

        model.addAttribute("activeNav", "cards");
        model.addAttribute("accountForm", form);
        model.addAttribute("kinds", AccountKind.values());
        model.addAttribute("editing", true);
        model.addAttribute("accountId", id);
        return "app/account-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("accountForm") AccountForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("accountId", id);
            model.addAttribute("kinds", AccountKind.values());
            return "app/account-form";
        }
        accounts.update(currentUser.requireEffectiveId(), id, form);
        flash.addFlashAttribute("ok", "account.updated");
        return "redirect:/app/cards";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            accounts.delete(currentUser.requireEffectiveId(), id);
            flash.addFlashAttribute("ok", "account.deleted");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/cards";
    }

    private static AccountForm emptyForm() {
        AccountForm f = new AccountForm();
        f.setKind(AccountKind.CARD);
        return f;
    }

    private YearMonth parseOrCurrent(String ym) {
        if (ym == null || ym.isBlank()) return YearMonth.now(clock);
        try {
            return YearMonth.parse(ym);
        } catch (Exception e) {
            return YearMonth.now(clock);
        }
    }
}
