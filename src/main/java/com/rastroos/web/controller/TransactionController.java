package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
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
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.service.TransactionService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.dto.TransactionFilter;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.form.TransactionForm;

import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) para /app/expenses — listagem com filtros, criar / editar /
 * deletar e marcar como pago.
 */
@Controller
@RequestMapping("/app/expenses")
@PreAuthorize("isAuthenticated()")
public class TransactionController {

    private final CurrentUser currentUser;
    private final TransactionService service;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Clock clock;

    public TransactionController(CurrentUser currentUser,
                                 TransactionService service,
                                 AccountRepository accounts,
                                 CategoryRepository categories,
                                 Clock clock) {
        this.currentUser = currentUser;
        this.service = service;
        this.accounts = accounts;
        this.categories = categories;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(value = "ym", required = false) String ym,
                       @RequestParam(value = "paid", required = false) String paid,
                       @RequestParam(value = "fixed", required = false) String fixed,
                       @RequestParam(value = "accountId", required = false) UUID accountId,
                       @RequestParam(value = "categoryId", required = false) String categoryId,
                       @RequestParam(value = "q", required = false) String search,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                       Model model) {
        YearMonth period = parseOrCurrent(ym);
        UUID userId = currentUser.requireId();

        TransactionFilter filter = new TransactionFilter(
                TransactionFilter.PaidFilter.parse(paid),
                accountId,
                categoryId,
                TransactionFilter.FixedFilter.parse(fixed),
                search
        );
        TransactionsPageView view = service.listForMonth(userId, period, filter, page, size);

        model.addAttribute("activeNav", "expenses");
        model.addAttribute("period", period);
        model.addAttribute("filter", filter);
        model.addAttribute("view", view);
        model.addAttribute("accounts", accounts.findAllByUserIdOrderByNameAsc(userId));
        model.addAttribute("categories", categories.findAllByOrderBySortOrderAsc());
        return "app/expenses";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "ym", required = false) String ym, Model model) {
        UUID userId = currentUser.requireId();
        prepareFormModel(model, userId, emptyForm(parseOrCurrent(ym)), false, null);
        return "app/transaction-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("transactionForm") TransactionForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        UUID userId = currentUser.requireId();
        if (binding.hasErrors()) {
            prepareFormModel(model, userId, form, false, null);
            return "app/transaction-form";
        }
        try {
            List<Transaction> created = service.create(userId, form);
            flash.addFlashAttribute("ok",
                    created.size() > 1 ? "transaction.createdInstallments" : "transaction.created");
        } catch (IllegalArgumentException e) {
            binding.rejectValue("amount", e.getMessage());
            prepareFormModel(model, userId, form, false, null);
            return "app/transaction-form";
        }
        return "redirect:/app/expenses";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        UUID userId = currentUser.requireId();
        Transaction t = service.require(userId, id);

        TransactionForm form = new TransactionForm();
        form.setDescription(t.getDescription());
        form.setAccountId(t.getAccountId());
        form.setCategoryId(t.getCategoryId());
        form.setAmount(MoneyDto.fromCents(t.getAmountCents()));
        form.setDueDate(t.getDueDate());
        form.setFixed(t.isFixed());
        form.setPaid(t.isPaid());
        form.setInstallments(1);

        prepareFormModel(model, userId, form, true, id);
        return "app/transaction-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("transactionForm") TransactionForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        UUID userId = currentUser.requireId();
        if (binding.hasErrors()) {
            prepareFormModel(model, userId, form, true, id);
            return "app/transaction-form";
        }
        try {
            service.update(userId, id, form);
            flash.addFlashAttribute("ok", "transaction.updated");
        } catch (IllegalArgumentException e) {
            binding.rejectValue("amount", e.getMessage());
            prepareFormModel(model, userId, form, true, id);
            return "app/transaction-form";
        }
        return "redirect:/app/expenses";
    }

    @PostMapping("/{id}/toggle-paid")
    public String togglePaid(@PathVariable UUID id,
                             @RequestParam(value = "redirect", required = false) String redirect,
                             RedirectAttributes flash) {
        Transaction t = service.togglePaid(currentUser.requireId(), id);
        flash.addFlashAttribute("ok",
                t.isPaid() ? "transaction.markedPaid" : "transaction.markedUnpaid");
        return "redirect:" + (redirect == null || redirect.isBlank() ? "/app/expenses" : redirect);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        service.delete(currentUser.requireId(), id);
        flash.addFlashAttribute("ok", "transaction.deleted");
        return "redirect:/app/expenses";
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void prepareFormModel(Model model, UUID userId, TransactionForm form,
                                  boolean editing, UUID id) {
        model.addAttribute("activeNav", "expenses");
        model.addAttribute("transactionForm", form);
        model.addAttribute("editing", editing);
        if (id != null) model.addAttribute("transactionId", id);
        model.addAttribute("accountOptions", accounts.findAllByUserIdOrderByNameAsc(userId));

        List<Category> categoryList = categories.findAllByOrderBySortOrderAsc();
        model.addAttribute("categoryOptions", categoryList);
    }

    private TransactionForm emptyForm(YearMonth period) {
        TransactionForm f = new TransactionForm();
        f.setDueDate(period.atDay(1));
        f.setInstallments(1);
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
