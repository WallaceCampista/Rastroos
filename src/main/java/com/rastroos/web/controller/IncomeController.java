package com.rastroos.web.controller;

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

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.IncomeSource;
import com.rastroos.domain.service.IncomeService;
import com.rastroos.domain.service.IncomeSourceService;
import com.rastroos.domain.service.MonthlyFinanceAggregator;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.IncomeDeleteView;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomeSourceDto;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.form.IncomeForm;
import com.rastroos.web.form.IncomeSourceForm;
import com.rastroos.web.support.PeriodResolver;

import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) para /app/income — listagem com filtros, lançamentos
 * avulsos e as receitas fixas (o salário de uma empresa cadastrada).
 *
 * <p>Receita não tem categoria: para lançar bastam o valor e a empresa.
 * A exclusão de um lançamento gerado por uma receita fixa oferece o mesmo
 * escopo da exclusão de conta — só este, deste mês em diante, ou tudo.
 */
@Controller
@RequestMapping("/app/income")
@PreAuthorize("isAuthenticated()")
public class IncomeController {

    private final CurrentUser currentUser;
    private final IncomeService service;
    private final IncomeSourceService sources;
    private final MonthlyFinanceAggregator aggregator;
    private final PeriodResolver periodResolver;

    public IncomeController(CurrentUser currentUser,
                            IncomeService service,
                            IncomeSourceService sources,
                            MonthlyFinanceAggregator aggregator,
                            PeriodResolver periodResolver) {
        this.currentUser = currentUser;
        this.service = service;
        this.sources = sources;
        this.aggregator = aggregator;
        this.periodResolver = periodResolver;
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
        model.addAttribute("sources", sources.list(userId, period));
        model.addAttribute("incomeChartJson",
                currentUser.isMaskActive() ? "{\"points\":[]}" : buildIncomeChartJson(userId, period));
        return "app/income";
    }

    // ── Lançamento avulso / do mês ───────────────────────────────────────

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
            IncomeService.CreateResult result = service.createOrConfirm(userId, form);
            // Com receita fixa escolhida, a ocorrência do mês é confirmada — a
            // mensagem precisa dizer isso, senão parece que nada foi lançado.
            flash.addFlashAttribute("ok",
                    result.confirmed() ? "income.confirmed" : "income.created");
        } catch (IllegalArgumentException e) {
            rejectDomainError(binding, e);
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
        form.setSourceId(existing.getSourceId());
        form.setSource(existing.getSource());
        form.setAmount(MoneyDto.fromCents(existing.getAmountCents()));
        form.setIncomeDate(existing.getIncomeDate());
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
            rejectDomainError(binding, e);
            prepareFormModel(model, form, true, id);
            return "app/income-form";
        }
        return "redirect:/app/income";
    }

    // ── Exclusão (mesmo esquema de "excluir conta") ──────────────────────

    @GetMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String deleteConfirm(@PathVariable UUID id, Model model) {
        UUID userId = currentUser.requireEffectiveId();
        IncomeDto income = service.get(userId, id);
        YearMonth period = YearMonth.from(income.incomeDate());

        IncomeSourceDto source = income.sourceId() == null
                ? null
                : sources.summary(userId, income.sourceId(), period);

        model.addAttribute("activeNav", "income");
        model.addAttribute("del", new IncomeDeleteView(
                income.id(),
                income.source(),
                income.incomeDate(),
                source,
                period.toString(),
                monthLabel(period),
                source == null ? 1L : sources.countIncomes(userId, source.id()),
                source == null ? 1L : sources.countIncomesFrom(userId, source.id(), period)));
        return "app/income-delete-confirm";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String delete(@PathVariable UUID id,
                         @RequestParam(value = "scope", required = false) String scope,
                         @RequestParam(value = "ym", required = false) String ym,
                         RedirectAttributes flash) {
        UUID userId = currentUser.requireEffectiveId();
        Income income = service.require(userId, id);
        UUID sourceId = income.getSourceId();

        // Sem fonte recorrente, o único escopo possível é o próprio lançamento —
        // um "apagar tudo" vindo de um request forjado não pode virar outra coisa.
        if (sourceId == null || scope == null || "ONE".equalsIgnoreCase(scope)) {
            service.delete(userId, id);
            flash.addFlashAttribute("ok", "income.deleted");
            return "redirect:/app/income";
        }
        return removeSource(userId, sourceId, scope, ym, flash);
    }

    /**
     * Confirma (ou desfaz) que o dinheiro caiu. Enquanto não confirmado, o
     * recebimento programado não conta como receita em lugar nenhum.
     */
    @PostMapping("/{id}/toggle-received")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String toggleReceived(@PathVariable UUID id,
                                 @RequestParam(value = "ym", required = false) String ym,
                                 RedirectAttributes flash) {
        Income updated = service.toggleReceived(currentUser.requireEffectiveId(), id);
        flash.addFlashAttribute("ok",
                updated.isReceived() ? "income.markedReceived" : "income.markedPending");
        return "redirect:/app/income?ym=" + parseOrCurrent(ym);
    }

    // ── Receita fixa (empresa que paga todo mês) ─────────────────────────

    @GetMapping("/sources/new")
    public String newSourceForm(@RequestParam(value = "ym", required = false) String ym,
                                Model model) {
        IncomeSourceForm form = new IncomeSourceForm();
        form.setPayBusinessDay((short) 5);
        form.setStartMonth(parseOrCurrent(ym));
        prepareSourceFormModel(model, form, false, null);
        return "app/income-source-form";
    }

    @PostMapping("/sources/new")
    public String createSource(@Valid @ModelAttribute("incomeSourceForm") IncomeSourceForm form,
                               BindingResult binding,
                               Model model,
                               RedirectAttributes flash) {
        UUID userId = currentUser.requireEffectiveId();
        if (binding.hasErrors()) {
            prepareSourceFormModel(model, form, false, null);
            return "app/income-source-form";
        }
        try {
            sources.create(userId, form);
            flash.addFlashAttribute("ok", "incomeSource.created");
        } catch (IllegalArgumentException e) {
            rejectSourceError(binding, e);
            prepareSourceFormModel(model, form, false, null);
            return "app/income-source-form";
        }
        return "redirect:/app/income";
    }

    @GetMapping("/sources/{id}/edit")
    public String editSourceForm(@PathVariable UUID id, Model model) {
        IncomeSource existing = sources.require(currentUser.requireEffectiveId(), id);

        IncomeSourceForm form = new IncomeSourceForm();
        form.setName(existing.getName());
        form.setAmount(MoneyDto.fromCents(existing.getAmountCents()));
        form.setPayBusinessDay(existing.getPayBusinessDay());
        form.setNote(existing.getNote());

        prepareSourceFormModel(model, form, true, id);
        return "app/income-source-form";
    }

    @PostMapping("/sources/{id}/edit")
    public String updateSource(@PathVariable UUID id,
                               @Valid @ModelAttribute("incomeSourceForm") IncomeSourceForm form,
                               BindingResult binding,
                               Model model,
                               RedirectAttributes flash) {
        UUID userId = currentUser.requireEffectiveId();
        if (binding.hasErrors()) {
            prepareSourceFormModel(model, form, true, id);
            return "app/income-source-form";
        }
        try {
            sources.update(userId, id, form);
            flash.addFlashAttribute("ok", "incomeSource.updated");
        } catch (IllegalArgumentException e) {
            rejectSourceError(binding, e);
            prepareSourceFormModel(model, form, true, id);
            return "app/income-source-form";
        }
        return "redirect:/app/income";
    }

    @GetMapping("/sources/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String deleteSourceConfirm(@PathVariable UUID id,
                                      @RequestParam(value = "ym", required = false) String ym,
                                      Model model) {
        UUID userId = currentUser.requireEffectiveId();
        YearMonth period = parseOrCurrent(ym);
        IncomeSourceDto source = sources.summary(userId, id, period);

        model.addAttribute("activeNav", "income");
        model.addAttribute("del", new IncomeDeleteView(
                null,
                source.name(),
                null,
                source,
                period.toString(),
                monthLabel(period),
                sources.countIncomes(userId, id),
                sources.countIncomesFrom(userId, id, period)));
        return "app/income-delete-confirm";
    }

    @PostMapping("/sources/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String deleteSource(@PathVariable UUID id,
                               @RequestParam(value = "scope", required = false) String scope,
                               @RequestParam(value = "ym", required = false) String ym,
                               RedirectAttributes flash) {
        return removeSource(currentUser.requireEffectiveId(), id, scope, ym, flash);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String removeSource(UUID userId, UUID sourceId, String scope, String ym,
                                RedirectAttributes flash) {
        IncomeSourceService.DeleteScope chosen = "FROM_MONTH".equalsIgnoreCase(scope)
                ? IncomeSourceService.DeleteScope.FROM_MONTH
                : IncomeSourceService.DeleteScope.ALL;
        try {
            sources.delete(userId, sourceId, chosen, parseOrCurrent(ym));
            flash.addFlashAttribute("ok",
                    chosen == IncomeSourceService.DeleteScope.FROM_MONTH
                            ? "incomeSource.closedFromMonth"
                            : "incomeSource.deleted");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/income";
    }

    private static void rejectDomainError(BindingResult binding, IllegalArgumentException e) {
        binding.rejectValue("income.sourceRequired".equals(e.getMessage())
                ? "source" : "amount", e.getMessage());
    }

    private static void rejectSourceError(BindingResult binding, IllegalArgumentException e) {
        binding.rejectValue("incomeSource.duplicateName".equals(e.getMessage())
                ? "name" : "amount", e.getMessage());
    }

    private void prepareFormModel(Model model, IncomeForm form,
                                  boolean editing, UUID id) {
        model.addAttribute("activeNav", "income");
        model.addAttribute("incomeForm", form);
        model.addAttribute("editing", editing);
        if (id != null) model.addAttribute("incomeId", id);
        model.addAttribute("sourceOptions",
                sources.listActive(currentUser.requireEffectiveId(), YearMonth.now()));
    }

    private void prepareSourceFormModel(Model model, IncomeSourceForm form,
                                        boolean editing, UUID id) {
        model.addAttribute("activeNav", "income");
        model.addAttribute("incomeSourceForm", form);
        model.addAttribute("editing", editing);
        if (id != null) model.addAttribute("incomeSourceId", id);
    }

    private IncomeForm emptyForm(YearMonth period) {
        IncomeForm f = new IncomeForm();
        f.setIncomeDate(period.atDay(1));
        return f;
    }

    private static String monthLabel(YearMonth ym) {
        String mes = ym.atDay(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM", Locale.of("pt", "BR")));
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + "/" + ym.getYear();
    }

    private YearMonth parseOrCurrent(String ym) {
        return periodResolver.resolve(ym);
    }
}
