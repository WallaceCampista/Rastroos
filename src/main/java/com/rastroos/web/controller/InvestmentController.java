package com.rastroos.web.controller;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.service.InvestmentService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.InvestmentChartData;
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
    private final ObjectMapper objectMapper;

    public InvestmentController(CurrentUser currentUser,
                                InvestmentService service,
                                ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String list(@RequestParam(value = "ym", required = false) String ym, Model model) {
        UUID userId = currentUser.requireEffectiveId();
        InvestmentsView view = service.load(userId);

        model.addAttribute("activeNav", "investments");
        model.addAttribute("period", parseOrCurrent(ym));
        model.addAttribute("view", view);
        model.addAttribute("investChartJson",
                buildInvestChartJson(view.chart(), currentUser.isMaskActive()));
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
        service.create(currentUser.requireEffectiveId(), form);
        flash.addFlashAttribute("ok", "investment.created");
        return "redirect:/app/investments";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        UUID userId = currentUser.requireEffectiveId();
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
        service.update(currentUser.requireEffectiveId(), id, form);
        flash.addFlashAttribute("ok", "investment.updated");
        return "redirect:/app/investments";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        service.delete(currentUser.requireEffectiveId(), id);
        flash.addFlashAttribute("ok", "investment.deleted");
        return "redirect:/app/investments";
    }

    /** Aporte num investimento existente (aba "Adicionar em existente" do modal). */
    @PostMapping("/deposit")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String deposit(@RequestParam("depositId") UUID depositId,
                          @RequestParam("depositAmount") java.math.BigDecimal depositAmount,
                          RedirectAttributes flash) {
        try {
            service.deposit(currentUser.requireEffectiveId(), depositId, depositAmount);
            flash.addFlashAttribute("ok", "investment.updated");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "investment.amountPositive");
        }
        return "redirect:/app/investments";
    }

    /** Resgate (saque) de um investimento — botão "Resgatar" no detalhe. */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String withdraw(@PathVariable UUID id,
                           @RequestParam("amount") java.math.BigDecimal amount,
                           RedirectAttributes flash) {
        try {
            service.withdraw(currentUser.requireEffectiveId(), id, amount);
            flash.addFlashAttribute("ok", "investment.withdrawn");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/investments";
    }

    /** Fragmento com as movimentações do investimento — abre ao clicar no card. */
    @GetMapping("/{id}/detail")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("detail",
                service.investmentDetail(currentUser.requireEffectiveId(), id));
        return "app/investment-detail";
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
            service.upsertHistory(currentUser.requireEffectiveId(), id, form);
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
        // Para a aba "Adicionar em existente" (só no modal de criação).
        if (!editing) {
            InvestmentsView v = service.load(currentUser.requireEffectiveId());
            model.addAttribute("existingInvestments",
                    java.util.stream.Stream.concat(v.piggies().stream(), v.portfolio().stream()).toList());
        }
    }

    private InvestmentForm emptyForm() {
        InvestmentForm f = new InvestmentForm();
        f.setKind(InvestmentKind.PIGGY);
        return f;
    }

    /** Serializa as séries dos gráficos (centavos → reais) para o JS. */
    private String buildInvestChartJson(InvestmentChartData chart, boolean masked) {
        if (masked || chart == null) {
            return "{\"labels\":[],\"total\":[],\"sparklines\":{}}";
        }
        List<Double> total = chart.totalCents().stream()
                .map(c -> c / 100.0).toList();
        Map<String, List<Double>> sparks = new LinkedHashMap<>();
        chart.sparklineCents().forEach((id, list) ->
                sparks.put(id, list.stream().map(c -> c / 100.0).toList()));
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "labels", chart.labels(),
                    "total", total,
                    "sparklines", sparks));
        } catch (JsonProcessingException e) {
            return "{\"labels\":[],\"total\":[],\"sparklines\":{}}";
        }
    }

    /** Período só alimenta a top bar (seletor de mês); os dados não mudam por mês. */
    private static YearMonth parseOrCurrent(String ym) {
        if (ym == null || ym.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(ym);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }
}
