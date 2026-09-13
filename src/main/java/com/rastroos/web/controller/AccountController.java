package com.rastroos.web.controller;

import java.time.YearMonth;
import java.util.Locale;
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
import com.rastroos.web.support.PeriodResolver;

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
    private final PeriodResolver periodResolver;

    public AccountController(CurrentUser currentUser, AccountService accounts,
                             PeriodResolver periodResolver) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.periodResolver = periodResolver;
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

    /** Corpo do modal com o detalhe da conta no mês (aberto ao clicar no card). */
    @GetMapping("/{id}/detail")
    public String detail(@PathVariable UUID id,
                         @RequestParam(value = "ym", required = false) String ym,
                         Model model) {
        YearMonth period = parseOrCurrent(ym);
        UUID userId = currentUser.requireEffectiveId();
        model.addAttribute("detail", accounts.accountDetail(userId, id, period));
        model.addAttribute("period", period);
        return "app/account-detail";
    }

    /** Pagar/reabrir a fatura (todos os lançamentos da conta no mês). */
    @PostMapping("/{id}/pay")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String pay(@PathVariable UUID id,
                      @RequestParam(value = "ym", required = false) String ym,
                      RedirectAttributes flash) {
        YearMonth period = parseOrCurrent(ym);
        accounts.payInvoice(currentUser.requireEffectiveId(), id, period);
        flash.addFlashAttribute("ok", "account.invoicePaid");
        // Volta com o detalhe aberto: a pessoa pagou de dentro dele.
        return "redirect:/app/cards?ym=" + period + "&open=" + id;
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
        form.setLast4(a.getLast4());
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

    /** Corpo do modal de confirmação: escolhe apagar tudo ou só do mês em diante. */
    @GetMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String deleteConfirm(@PathVariable UUID id,
                                @RequestParam(value = "ym", required = false) String ym,
                                Model model) {
        UUID userId = currentUser.requireEffectiveId();
        YearMonth period = parseOrCurrent(ym);
        model.addAttribute("activeNav", "cards");
        model.addAttribute("account", accounts.summary(userId, id, period));
        model.addAttribute("period", period);
        model.addAttribute("monthLabel", monthLabel(period));
        model.addAttribute("txTotal", accounts.countTransactions(userId, id));
        model.addAttribute("txFuture", accounts.countTransactionsFrom(userId, id, period));
        return "app/account-delete-confirm";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
    public String delete(@PathVariable UUID id,
                         @RequestParam(value = "scope", required = false) String scope,
                         @RequestParam(value = "ym", required = false) String ym,
                         RedirectAttributes flash) {
        AccountService.DeleteScope chosen = "FROM_MONTH".equalsIgnoreCase(scope)
                ? AccountService.DeleteScope.FROM_MONTH
                : AccountService.DeleteScope.ALL;
        try {
            accounts.delete(currentUser.requireEffectiveId(), id, chosen, parseOrCurrent(ym));
            flash.addFlashAttribute("ok", chosen == AccountService.DeleteScope.FROM_MONTH
                    ? "account.closedFromMonth"
                    : "account.deleted");
        } catch (IllegalStateException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/cards";
    }

    private static String monthLabel(YearMonth ym) {
        String mes = ym.atDay(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM", new Locale("pt", "BR")));
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + "/" + ym.getYear();
    }

    private static AccountForm emptyForm() {
        AccountForm f = new AccountForm();
        f.setKind(AccountKind.CARD);
        return f;
    }

    private YearMonth parseOrCurrent(String ym) {
        return periodResolver.resolve(ym);
    }
}
