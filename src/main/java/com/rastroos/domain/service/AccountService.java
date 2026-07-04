package com.rastroos.domain.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.dto.MoneyDto;
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

    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public AccountsView listForMonth(UUID userId, YearMonth ym) {
        List<Account> all = accounts.findAllByUserIdOrderByNameAsc(userId);
        Map<UUID, long[]> agg = aggregateByAccount(userId, ym);

        List<AccountSummaryDto> cards = new ArrayList<>();
        List<AccountSummaryDto> bills = new ArrayList<>();
        List<AccountSummaryDto> recurrent = new ArrayList<>();

        for (Account a : all) {
            AccountSummaryDto dto = toSummary(a, agg.get(a.getId()));
            switch (a.getKind()) {
                case CARD -> cards.add(dto);
                case BILL -> bills.add(dto);
                case RECURRENT -> recurrent.add(dto);
            }
        }
        return new AccountsView(cards, bills, recurrent);
    }

    @Transactional(readOnly = true)
    public List<AccountSummaryDto> topAccountsForMonth(UUID userId, YearMonth ym, int limit) {
        List<Account> all = accounts.findAllByUserIdOrderByNameAsc(userId);
        Map<UUID, long[]> agg = aggregateByAccount(userId, ym);

        return all.stream()
                .map(a -> toSummary(a, agg.get(a.getId())))
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

    private static AccountSummaryDto toSummary(Account a, long[] agg) {
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
                a.getCloseDay(),
                a.getDueDay(),
                MoneyDto.fromCents(total),
                MoneyDto.fromCents(paid),
                MoneyDto.fromCents(total - paid),
                pct,
                count
        );
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
    }

    private static String normalizeColor(String hex) {
        if (hex == null || hex.isBlank()) return null;
        String trimmed = hex.trim();
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }
}
