package com.rastroos.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.AccountDetailView;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.dto.TransactionDto;
import com.rastroos.web.form.AccountForm;

/**
 * Regras de negócio para contas (cartões / boletos / recorrentes).
 * Toda operação opera estritamente sobre {@code userId} do contexto;
 * acesso a conta de outro usuário → {@link ResourceNotFoundException}.
 */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final Clock clock;

    public AccountService(AccountRepository accounts, TransactionRepository transactions,
                          CategoryRepository categories, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountsView listForMonth(UUID userId, YearMonth ym) {
        List<Account> all = accounts.findAllByUserIdOrderByNameAsc(userId);
        Map<UUID, long[]> agg = aggregateByAccount(userId, ym);

        List<AccountSummaryDto> cards = new ArrayList<>();
        List<AccountSummaryDto> bills = new ArrayList<>();
        List<AccountSummaryDto> recurrent = new ArrayList<>();

        for (Account a : all) {
            AccountSummaryDto dto = toSummary(a, agg.get(a.getId()), ym);
            switch (a.getKind()) {
                case CARD -> cards.add(dto);
                case BILL -> bills.add(dto);
                case RECURRENT -> recurrent.add(dto);
            }
        }

        // KPIs do topo (igual ao protótipo): somam só os lançamentos FIXOS do mês;
        // "Contas" é a quantidade de contas fixas (não-cartão).
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        long totalFixed = 0L;
        long paidFixed = 0L;
        for (Transaction t : transactions.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(userId, start, end)) {
            if (t.isFixed()) {
                totalFixed += t.getAmountCents();
                if (t.isPaid()) paidFixed += t.getAmountCents();
            }
        }
        int fixedCount = (int) all.stream().filter(Account::isFixed).count();

        return new AccountsView(cards, bills, recurrent,
                MoneyDto.fromCents(totalFixed),
                MoneyDto.fromCents(paidFixed),
                MoneyDto.fromCents(totalFixed - paidFixed),
                fixedCount);
    }

    /** Detalhe de uma conta no mês (resumo + lançamentos ricos + gastos por
     *  categoria). 404 se de outro usuário. */
    @Transactional(readOnly = true)
    public AccountDetailView accountDetail(UUID userId, UUID accountId, YearMonth ym) {
        Account a = require(userId, accountId);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        AccountSummaryDto summary = toSummary(a, aggregateByAccount(userId, ym).get(accountId), ym);

        List<Transaction> txs = transactions
                .findAllByUserIdAndAccountIdAndDueDateBetween(userId, accountId, start, end).stream()
                .sorted(Comparator.comparing(Transaction::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<String, Category> catById = mapCategories();
        boolean english = isEnglish();

        List<TransactionDto> rows = txs.stream()
                .map(t -> toTxDto(t, a, catById.get(t.getCategoryId()), english))
                .toList();
        List<CategoryBreakdownDto> byCategory = categoryBreakdown(txs, catById, english);

        return new AccountDetailView(summary, ym.getYear(), rows, byCategory);
    }

    /**
     * Alterna o pagamento da fatura/conta no mês: se tudo está pago → reabre
     * (marca tudo em aberto); senão → paga tudo. Espelha o "Pagar fatura /
     * Reabrir" do protótipo. 404 se de outro usuário.
     */
    @Transactional
    public void payInvoice(UUID userId, UUID accountId, YearMonth ym) {
        require(userId, accountId);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        List<Transaction> txs =
                transactions.findAllByUserIdAndAccountIdAndDueDateBetween(userId, accountId, start, end);
        if (txs.isEmpty()) return;
        boolean allPaid = txs.stream().allMatch(Transaction::isPaid);
        Instant now = clock.instant();
        for (Transaction t : txs) {
            if (allPaid) {
                t.setPaid(false);
                t.setPaidAt(null);
            } else if (!t.isPaid()) {
                t.setPaid(true);
                t.setPaidAt(now);
            }
        }
        transactions.saveAll(txs);
    }

    @Transactional(readOnly = true)
    public List<AccountSummaryDto> topAccountsForMonth(UUID userId, YearMonth ym, int limit) {
        List<Account> all = accounts.findAllByUserIdOrderByNameAsc(userId);
        Map<UUID, long[]> agg = aggregateByAccount(userId, ym);

        return all.stream()
                .map(a -> toSummary(a, agg.get(a.getId()), ym))
                .filter(s -> s.total().signum() > 0)
                .sorted((x, y) -> y.total().compareTo(x.total()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public Account require(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("account.notFound"));
    }

    @Transactional
    public Account create(UUID userId, AccountForm form) {
        Account a = new Account();
        a.setUserId(userId);
        applyForm(a, form);
        return accounts.save(a);
    }

    @Transactional
    public Account update(UUID userId, UUID id, AccountForm form) {
        Account a = require(userId, id);
        applyForm(a, form);
        return accounts.save(a);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Account a = require(userId, id);
        if (transactions.countByUserIdAndAccountId(userId, id) > 0) {
            throw new IllegalStateException("account.hasTransactions");
        }
        accounts.delete(a);
    }

    private Map<UUID, long[]> aggregateByAccount(UUID userId, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        Map<UUID, long[]> map = new HashMap<>();
        for (Object[] row : transactions.aggregateByAccountAndPeriod(userId, start, end)) {
            UUID accountId = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            long paid = ((Number) row[2]).longValue();
            long count = ((Number) row[3]).longValue();
            map.put(accountId, new long[] { total, paid, count });
        }
        return map;
    }

    private AccountSummaryDto toSummary(Account a, long[] agg, YearMonth ym) {
        long total = agg == null ? 0L : agg[0];
        long paid  = agg == null ? 0L : agg[1];
        long count = agg == null ? 0L : agg[2];
        int pct = total > 0 ? (int) Math.round(paid * 100.0 / total) : 0;
        return new AccountSummaryDto(
                a.getId(),
                a.getName(),
                a.getKind(),
                a.getColorHex(),
                a.getIconText(),
                a.getLast4(),
                a.getCloseDay(),
                a.getDueDay(),
                MoneyDto.fromCents(total),
                MoneyDto.fromCents(paid),
                MoneyDto.fromCents(total - paid),
                pct,
                count,
                accountStatus(total, paid, a.getDueDay(), ym)
        );
    }

    /**
     * Status da conta no mês (igual ao protótipo): sem lançamentos → {@code none};
     * tudo pago → {@code paid}; senão compara o vencimento com hoje —
     * vencido → {@code overdue}, ≤ 3 dias → {@code soon}, senão {@code open}.
     */
    private String accountStatus(long total, long paid, Short dueDay, YearMonth ym) {
        if (total <= 0) return "none";
        if (paid >= total) return "paid";
        int day = Math.min(dueDay != null ? dueDay : 15, ym.lengthOfMonth());
        LocalDate dueDate = ym.atDay(day);
        LocalDate today = LocalDate.now(clock);
        if (dueDate.isBefore(today)) return "overdue";
        if (!dueDate.isAfter(today.plusDays(3))) return "soon";
        return "open";
    }

    private Map<String, Category> mapCategories() {
        Map<String, Category> map = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            map.put(c.getId(), c);
        }
        return map;
    }

    private static TransactionDto toTxDto(Transaction t, Account account, Category category, boolean english) {
        return new TransactionDto(
                t.getId(),
                t.getDescription(),
                t.getAccountId(),
                account == null ? "" : account.getName(),
                account == null ? null : account.getColorHex(),
                t.getCategoryId(),
                category == null ? t.getCategoryId()
                        : (english ? category.getNameEn() : category.getNamePt()),
                category == null ? null : category.getColorHex(),
                MoneyDto.fromCents(t.getAmountCents()),
                t.getDueDate(),
                t.isFixed(),
                t.isPaid(),
                t.getPaidAt(),
                t.getInstallmentCurrent(),
                t.getInstallmentTotal());
    }

    /** Soma dos lançamentos por categoria (desc), para o "Gastos por categoria". */
    private static List<CategoryBreakdownDto> categoryBreakdown(List<Transaction> txs,
                                                                Map<String, Category> catById,
                                                                boolean english) {
        Map<String, Long> sums = new LinkedHashMap<>();
        for (Transaction t : txs) {
            sums.merge(t.getCategoryId(), t.getAmountCents(), Long::sum);
        }
        return sums.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Category c = catById.get(e.getKey());
                    String name = c == null ? e.getKey()
                            : (english ? c.getNameEn() : c.getNamePt());
                    String color = c == null ? "#94a3b8" : c.getColorHex();
                    return new CategoryBreakdownDto(e.getKey(), name, color, MoneyDto.fromCents(e.getValue()));
                })
                .toList();
    }

    private static boolean isEnglish() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage());
    }

    private static void applyForm(Account a, AccountForm form) {
        a.setName(form.getName().trim());
        a.setKind(form.getKind());
        a.setColorHex(normalizeColor(form.getColorHex()));
        a.setIconText(form.getIconText());
        a.setCategoryId(form.getCategoryId());
        a.setFixed(form.getKind() != AccountKind.CARD && form.isFixed());
        a.setCloseDay(form.getKind() == AccountKind.CARD ? form.getCloseDay() : null);
        a.setDueDay(form.getKind() == AccountKind.CARD ? form.getDueDay() : null);
        a.setLast4(form.getKind() == AccountKind.CARD ? normalizeLast4(form.getLast4()) : null);
    }

    private static String normalizeLast4(String last4) {
        if (last4 == null) return null;
        String trimmed = last4.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeColor(String hex) {
        if (hex == null || hex.isBlank()) return null;
        String trimmed = hex.trim();
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }
}
