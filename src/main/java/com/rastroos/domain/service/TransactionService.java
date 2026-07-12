package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.dto.TransactionDto;
import com.rastroos.web.dto.TransactionFilter;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.form.TransactionForm;

/**
 * Regras de negócio para lançamentos (despesas).
 *
 * <p>Isolamento estrito por {@code userId}: nenhum método aceita transação
 * sem ter o usuário corrente. Acesso a id de outro usuário →
 * {@link ResourceNotFoundException}.
 *
 * <p>Suporta:
 * <ul>
 *   <li><b>Parcelas</b> — ao criar com {@code installments > 1}, o valor
 *       informado é o <b>total da compra</b>, dividido igualmente entre as N
 *       parcelas (os centavos de resto vão para as primeiras). Gera N
 *       transações com {@code installmentCurrent} 1..N, {@code installmentTotal=N}
 *       e {@code dueDate} a partir do <b>mês seguinte</b> ao vencimento
 *       informado (1ª parcela = mês seguinte, 2ª = dois meses depois, ...).</li>
 *   <li><b>Recorrência</b> — flag {@code fixed=true} marca o lançamento como
 *       recorrente. A expansão para meses futuros entra em iteração posterior;
 *       por enquanto, apenas o primeiro lançamento é persistido (assim como
 *       no design original).</li>
 * </ul>
 */
@Service
public class TransactionService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions,
                              AccountRepository accounts,
                              CategoryRepository categories) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public TransactionsPageView listForMonth(UUID userId, YearMonth ym,
                                              TransactionFilter filter,
                                              int page, int size) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by("createdAt")));

        TransactionFilter f = filter == null ? TransactionFilter.empty() : filter;
        Boolean paid = switch (f.paid()) {
            case ALL -> null;
            case PAID -> Boolean.TRUE;
            case UNPAID -> Boolean.FALSE;
        };
        Boolean fixed = switch (f.fixed()) {
            case ALL -> null;
            case FIXED -> Boolean.TRUE;
            case ONE_OFF -> Boolean.FALSE;
        };
        String categoryId = blankToNull(f.categoryId());
        // Postgres não infere tipo do LOWER(NULL) — passamos "" e a query trata como "sem filtro"
        String search = (f.search() == null) ? "" : f.search().trim();

        Page<Transaction> result = transactions.searchByFilters(
                userId, start, end, paid, fixed, f.accountId(), categoryId, search, pageable);

        Map<UUID, Account> accountById = mapAccounts(userId);
        Map<String, Category> categoryById = mapCategories();
        Locale locale = LocaleContextHolder.getLocale();
        boolean english = "en".equalsIgnoreCase(locale.getLanguage());

        List<TransactionDto> items = result.getContent().stream()
                .map(t -> toDto(t, accountById, categoryById, english))
                .toList();

        List<Object[]> totalsRows = transactions.totalsByFilters(
                userId, start, end, paid, fixed, f.accountId(), categoryId, search);
        Object[] totals = totalsRows.isEmpty()
                ? new Object[] { 0L, 0L }
                : totalsRows.get(0);
        long totalAmountCents = ((Number) totals[0]).longValue();
        long totalPaidCents   = ((Number) totals[1]).longValue();

        return new TransactionsPageView(
                items,
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                MoneyDto.fromCents(totalAmountCents),
                MoneyDto.fromCents(totalPaidCents)
        );
    }

    @Transactional(readOnly = true)
    public TransactionDto get(UUID userId, UUID id) {
        Transaction t = require(userId, id);
        Map<UUID, Account> accountById = mapAccounts(userId);
        Map<String, Category> categoryById = mapCategories();
        boolean english = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        return toDto(t, accountById, categoryById, english);
    }

    @Transactional
    public List<Transaction> create(UUID userId, TransactionForm form) {
        ensureAccountBelongsToUser(userId, form.getAccountId());
        ensureCategoryExists(form.getCategoryId());

        int n = Math.max(1, Math.min(60, form.getInstallments()));
        long totalCents = form.getAmount().movePointRight(2).longValueExact();
        if (totalCents <= 0) {
            throw new IllegalArgumentException("transaction.amountPositive");
        }
        // Cada parcela precisa ser > 0 (CHECK ck_tx_amount_positive no banco).
        if (n > 1 && totalCents < n) {
            throw new IllegalArgumentException("transaction.installmentTooSmall");
        }

        long[] installmentCents = splitCents(totalCents, n);
        // Compra parcelada: 1ª parcela cai no mês seguinte ao vencimento informado.
        LocalDate firstDueDate = (n > 1) ? form.getDueDate().plusMonths(1) : form.getDueDate();

        List<Transaction> created = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Transaction t = new Transaction();
            t.setUserId(userId);
            t.setAccountId(form.getAccountId());
            t.setCategoryId(form.getCategoryId());
            t.setDescription(form.getDescription().trim());
            t.setAmountCents(installmentCents[i]);
            t.setDueDate(firstDueDate.plusMonths(i));
            t.setFixed(form.isFixed());
            t.setPaid(n == 1 && form.isPaid());
            if (t.isPaid()) t.setPaidAt(Instant.now());
            if (n > 1) {
                t.setInstallmentCurrent((short) (i + 1));
                t.setInstallmentTotal((short) n);
            }
            created.add(transactions.save(t));
        }
        return created;
    }

    /**
     * Divide {@code totalCents} em {@code n} parcelas inteiras de centavos cuja
     * soma é exatamente {@code totalCents}. O resto (0..n-1 centavos) é
     * distribuído uma unidade por vez nas primeiras parcelas, de forma que a
     * soma feche e nenhuma parcela fique menor que a base.
     *
     * <p>Ex.: 100,00 em 3x → {@code [3334, 3333, 3333]} centavos.
     */
    private static long[] splitCents(long totalCents, int n) {
        long base = totalCents / n;
        long remainder = totalCents % n;
        long[] parts = new long[n];
        for (int i = 0; i < n; i++) {
            parts[i] = base + (i < remainder ? 1 : 0);
        }
        return parts;
    }

    @Transactional
    public Transaction update(UUID userId, UUID id, TransactionForm form) {
        Transaction t = require(userId, id);
        ensureAccountBelongsToUser(userId, form.getAccountId());
        ensureCategoryExists(form.getCategoryId());

        long amountCents = form.getAmount().movePointRight(2).longValueExact();
        if (amountCents <= 0) {
            throw new IllegalArgumentException("transaction.amountPositive");
        }
        t.setDescription(form.getDescription().trim());
        t.setAccountId(form.getAccountId());
        t.setCategoryId(form.getCategoryId());
        t.setAmountCents(amountCents);
        t.setDueDate(form.getDueDate());
        t.setFixed(form.isFixed());
        boolean wasPaid = t.isPaid();
        t.setPaid(form.isPaid());
        if (form.isPaid() && !wasPaid) {
            t.setPaidAt(Instant.now());
        } else if (!form.isPaid()) {
            t.setPaidAt(null);
        }
        return transactions.save(t);
    }

    @Transactional
    public Transaction togglePaid(UUID userId, UUID id) {
        Transaction t = require(userId, id);
        if (t.isPaid()) {
            t.setPaid(false);
            t.setPaidAt(null);
        } else {
            t.setPaid(true);
            t.setPaidAt(Instant.now());
        }
        return transactions.save(t);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Transaction t = require(userId, id);
        transactions.delete(t);
    }

    @Transactional(readOnly = true)
    public Transaction require(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("transaction.notFound"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void ensureAccountBelongsToUser(UUID userId, UUID accountId) {
        accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("account.notFound"));
    }

    private void ensureCategoryExists(String categoryId) {
        if (!categories.existsById(categoryId)) {
            throw new ResourceNotFoundException("category.notFound");
        }
    }

    private Map<UUID, Account> mapAccounts(UUID userId) {
        Map<UUID, Account> map = new HashMap<>();
        for (Account a : accounts.findAllByUserIdOrderByNameAsc(userId)) {
            map.put(a.getId(), a);
        }
        return map;
    }

    private Map<String, Category> mapCategories() {
        Map<String, Category> map = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            map.put(c.getId(), c);
        }
        return map;
    }

    private static TransactionDto toDto(Transaction t,
                                        Map<UUID, Account> accountById,
                                        Map<String, Category> categoryById,
                                        boolean english) {
        Account account = accountById.get(t.getAccountId());
        Category category = categoryById.get(t.getCategoryId());
        BigDecimal amount = MoneyDto.fromCents(t.getAmountCents());

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
                amount,
                t.getDueDate(),
                t.isFixed(),
                t.isPaid(),
                t.getPaidAt(),
                t.getInstallmentCurrent(),
                t.getInstallmentTotal()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
