package com.rastroos.web.controller;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.service.InvestmentService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.form.InvestmentForm;
import com.rastroos.web.form.InvestmentHistoryForm;

import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) para /app/investments — lista cofrinhos e carteira,
 * formulário CRUD e ações para adicionar histórico mensal.
 */
@Controller
@RequestMapping("/app/investments")
@PreAuthorize("isAuthenticated()")
public class InvestmentController {

    private final CurrentUser currentUser;
    private final InvestmentService service;

    public InvestmentController(CurrentUser currentUser,
                                InvestmentService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        UUID userId = currentUser.requireId();
        InvestmentsView view = service.load(userId);

        model.addAttribute("activeNav", "investments");
        model.addAttribute("view", view);
        model.addAttribute("kinds", InvestmentKind.values());
        if (!model.containsAttribute("historyForm")) {
            InvestmentHistoryForm hf = new InvestmentHistoryForm();
            hf.setYearMonth(YearMonth.now().toString());
            model.addAttribute("historyForm", hf);
        }
        return "app/investments";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        prepareFormModel(model, emptyForm(), false, null);
        return "app/investment-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("investmentForm") InvestmentForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            prepareFormModel(model, form, false, null);
            return "app/investment-form";
        }
        service.create(currentUser.requireId(), form);
        flash.addFlashAttribute("ok", "investment.created");
        return "redirect:/app/investments";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        UUID userId = currentUser.requireId();
        Investment inv = service.require(userId, id);

        InvestmentForm form = new InvestmentForm();
        form.setName(inv.getName());
        form.setKind(inv.getKind());
        form.setAmount(MoneyDto.fromCents(inv.getAmountCents()));
        form.setGoal(inv.getGoalCents() == null ? null : MoneyDto.fromCents(inv.getGoalCents()));
        form.setRateLabel(inv.getRateLabel());
        form.setMonthlyReturn(inv.getMonthlyReturnCents() == null
                ? null : MoneyDto.fromCents(inv.getMonthlyReturnCents()));
        form.setColorHex(inv.getColorHex());
        form.setIconText(inv.getIconText());

        prepareFormModel(model, form, true, id);
        return "app/investment-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("investmentForm") InvestmentForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            prepareFormModel(model, form, true, id);
            return "app/investment-form";
        }
        service.update(currentUser.requireId(), id, form);
        flash.addFlashAttribute("ok", "investment.updated");
        return "redirect:/app/investments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        service.delete(currentUser.requireId(), id);
        flash.addFlashAttribute("ok", "investment.deleted");
        return "redirect:/app/investments";
    }

    @PostMapping("/{id}/history")
    public String addHistory(@PathVariable UUID id,
                             @Valid @ModelAttribute("historyForm") InvestmentHistoryForm form,
                             BindingResult binding,
                             RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "investment.historyInvalid");
            return "redirect:/app/investments";
        }
        try {
            service.upsertHistory(currentUser.requireId(), id, form);
            flash.addFlashAttribute("ok", "investment.historyUpdated");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/investments";
    }

    // ── helpers ──────────────────────────────────────────────

    private void prepareFormModel(Model model, InvestmentForm form,
                                  boolean editing, UUID id) {
        model.addAttribute("activeNav", "investments");
        model.addAttribute("investmentForm", form);
        model.addAttribute("editing", editing);
        if (id != null) model.addAttribute("investmentId", id);
        model.addAttribute("kinds", InvestmentKind.values());
    }

    private InvestmentForm emptyForm() {
        InvestmentForm f = new InvestmentForm();
        f.setKind(InvestmentKind.PIGGY);
        return f;
    }
}
