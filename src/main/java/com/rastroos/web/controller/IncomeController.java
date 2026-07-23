package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;

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

import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.service.IncomeService;
import com.rastroos.domain.service.MonthlyFinanceAggregator;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.form.IncomeForm;

import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) para /app/income — listagem com filtros, criar /
 * editar / deletar receitas do usuário corrente.
 */
@Controller
@RequestMapping("/app/income")
@PreAuthorize("isAuthenticated()")
public class IncomeController {

    private final CurrentUser currentUser;
    private final IncomeService service;
    private final CategoryRepository categories;
    private final MonthlyFinanceAggregator aggregator;
    private final Clock clock;

    public IncomeController(CurrentUser currentUser,
                            IncomeService service,
                            CategoryRepository categories,
                            MonthlyFinanceAggregator aggregator,
                            Clock clock) {
        this.currentUser = currentUser;
        this.service = service;
        this.categories = categories;
        this.aggregator = aggregator;
        this.clock = clock;
    }

    /** Série dos últimos 6 meses de receita (para o gráfico do topo). */
    private String buildIncomeChartJson(UUID userId, YearMonth period) {
        Locale locale = LocaleContextHolder.getLocale();
        StringBuilder sb = new StringBuilder("{\"points\":[");
        List<YearMonth> months = MonthlyFinanceAggregator.trailingAxis(period, 6);
        for (int i = 0; i < months.size(); i++) {
            YearMonth m = months.get(i);
            double v = aggregator.summarize(userId, m, 0L, false).received().doubleValue();
            String lbl = m.getMonth().getDisplayName(TextStyle.SHORT, locale).replace(".", "");
            if (!lbl.isEmpty()) {
                lbl = Character.toUpperCase(lbl.charAt(0)) + lbl.substring(1);
            }
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"x\":\"").append(lbl).append("\",\"y\":").append(v).append("}");
        }
        return sb.append("]}").toString();
    }

    @GetMapping
    public String list(@RequestParam(value = "ym", required = false) String ym,
                       @RequestParam(value = "categoryId", required = false) String categoryId,
                       @RequestParam(value = "q", required = false) String search,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                       Model model) {
        YearMonth period = parseOrCurrent(ym);
        UUID userId = currentUser.requireEffectiveId();

        IncomeFilter filter = new IncomeFilter(categoryId, search);
        IncomesPageView view = service.listForMonth(userId, period, filter, page, size);

        model.addAttribute("activeNav", "income");
        model.addAttribute("period", period);
        model.addAttribute("filter", filter);
        model.addAttribute("view", view);
        model.addAttribute("categories", categories.findAllByOrderBySortOrderAsc());
        model.addAttribute("incomeChartJson",
                currentUser.isMaskActive() ? "{\"points\":[]}" : buildIncomeChartJson(userId, period));
        return "app/income";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "ym", required = false) String ym, Model model) {
        prepareFormModel(model, emptyForm(parseOrCurrent(ym)), false, null);
        return "app/income-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("incomeForm") IncomeForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        UUID userId = currentUser.requireEffectiveId();
        if (binding.hasErrors()) {
            prepareFormModel(model, form, false, null);
            return "app/income-form";
        }
        try {
            service.create(userId, form);
            flash.addFlashAttribute("ok", "income.created");
        } catch (IllegalArgumentException e) {
            binding.rejectValue("amount", e.getMessage());
            prepareFormModel(model, form, false, null);
            return "app/income-form";
        }
        return "redirect:/app/income";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        UUID userId = currentUser.requireEffectiveId();
        Income existing = service.require(userId, id);

        IncomeForm form = new IncomeForm();
        form.setSource(existing.getSource());
        form.setAmount(MoneyDto.fromCents(existing.getAmountCents()));
        form.setIncomeDate(existing.getIncomeDate());
        form.setCategoryId(existing.getCategory());
        form.setNote(existing.getNote());

        prepareFormModel(model, form, true, id);
        return "app/income-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("incomeForm") IncomeForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        UUID userId = currentUser.requireEffectiveId();
        if (binding.hasErrors()) {
            prepareFormModel(model, form, true, id);
            return "app/income-form";
        }
        try {
            service.update(userId, id, form);
            flash.addFlashAttribute("ok", "income.updated");
        } catch (IllegalArgumentException e) {
            binding.rejectValue("amount", e.getMessage());
            prepareFormModel(model, form, true, id);
            return "app/income-form";
        }
        return "redirect:/app/income";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        service.delete(currentUser.requireEffectiveId(), id);
        flash.addFlashAttribute("ok", "income.deleted");
        return "redirect:/app/income";
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void prepareFormModel(Model model, IncomeForm form,
                                  boolean editing, UUID id) {
        model.addAttribute("activeNav", "income");
        model.addAttribute("incomeForm", form);
        model.addAttribute("editing", editing);
        if (id != null) model.addAttribute("incomeId", id);
        List<Category> categoryList = categories.findAllByOrderBySortOrderAsc();
        model.addAttribute("categoryOptions", categoryList);
    }

    private IncomeForm emptyForm(YearMonth period) {
        IncomeForm f = new IncomeForm();
        f.setIncomeDate(period.atDay(1));
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
