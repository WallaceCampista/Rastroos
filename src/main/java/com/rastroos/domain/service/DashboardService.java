package com.rastroos.domain.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.BalancePointDto;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.dto.UpcomingTransactionDto;

/**
 * Coleta os agregados da tela "Visão geral": KPIs do mês, séries diárias
 * de saldo, gastos por categoria, próximos vencimentos em aberto e contas
 * mais movimentadas do mês.
 *
 * <p>Tudo filtrado por {@code userId} — Service é o único ponto que sabe
 * sobre o usuário corrente; os controllers só passam o id adiante.
 */
@Service
public class DashboardService {

    /** Quantos itens na lista "próximos vencimentos". */
    public static final int UPCOMING_LIMIT = 5;

    /** Quantos itens na lista "top contas do mês". */
    public static final int TOP_ACCOUNTS_LIMIT = 6;

    /** Quantas categorias mostrar no donut (resto vira "outros"). */
    public static final int CATEGORY_DONUT_LIMIT = 6;

    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final AccountService accountService;

    public DashboardService(TransactionRepository transactions,
                            IncomeRepository incomes,
                            AccountRepository accounts,
                            CategoryRepository categories,
                            AccountService accountService) {
        this.transactions = transactions;
        this.incomes = incomes;
        this.accounts = accounts;
        this.categories = categories;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public DashboardModel load(UUID userId, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        long incomeCents = incomes.sumAmountByUserAndPeriod(userId, start, end);
        long spentCents = transactions.sumAmountByUserAndPeriod(userId, start, end);
        long paidCents = transactions.sumPaidByUserAndPeriod(userId, start, end);
        long toPayCents = Math.max(0L, spentCents - paidCents);
        long balanceCents = incomeCents - paidCents;

        DashboardKpisDto kpis = new DashboardKpisDto(
                MoneyDto.fromCents(incomeCents),
                MoneyDto.fromCents(spentCents),
                MoneyDto.fromCents(paidCents),
                MoneyDto.fromCents(toPayCents),
                MoneyDto.fromCents(balanceCents)
        );

        List<Transaction> monthTx =
                transactions.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(userId, start, end);
        List<Income> monthIncomes =
                incomes.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(userId, start, end);

        List<BalancePointDto> balanceSeries = buildBalanceSeries(ym, monthIncomes, monthTx);

        List<CategoryBreakdownDto> byCategory =
                buildCategoryBreakdown(userId, start, end);

        List<Transaction> upcomingTx = transactions.findUpcomingUnpaid(
                userId, start, PageRequest.of(0, UPCOMING_LIMIT));

        Map<UUID, String> accountNames = new HashMap<>();
        for (Account a : accounts.findAllByUserIdOrderByNameAsc(userId)) {
            accountNames.put(a.getId(), a.getName());
        }

        List<UpcomingTransactionDto> upcoming = upcomingTx.stream()
                .map(t -> new UpcomingTransactionDto(
                        t.getId(),
                        t.getDescription(),
                        t.getAccountId(),
                        accountNames.getOrDefault(t.getAccountId(), ""),
                        t.getCategoryId(),
                        MoneyDto.fromCents(t.getAmountCents()),
                        t.getDueDate(),
                        t.isFixed()
                ))
                .toList();

        List<AccountSummaryDto> topAccounts =
                accountService.topAccountsForMonth(userId, ym, TOP_ACCOUNTS_LIMIT);

        long entriesCount = monthTx.size();
        long accountsCount = accounts.countByUserId(userId);

        return new DashboardModel(
                start,
                end,
                accountsCount,
                entriesCount,
                kpis,
                balanceSeries,
                byCategory,
                upcoming,
                topAccounts
        );
    }

    /**
     * Calcula o saldo "corrido" por dia do mês: começa em 0, soma receita do
     * dia, subtrai despesa paga do dia. Devolve um ponto por dia (1..N).
     */
    private List<BalancePointDto> buildBalanceSeries(YearMonth ym,
                                                     List<Income> monthIncomes,
                                                     List<Transaction> monthTx) {
        int daysIn = ym.lengthOfMonth();
        long[] perDay = new long[daysIn + 1]; // 1-indexed

        for (Income i : monthIncomes) {
            int day = i.getIncomeDate().getDayOfMonth();
            perDay[day] += i.getAmountCents();
        }
        for (Transaction t : monthTx) {
            if (!t.isPaid()) continue;
            int day = t.getDueDate().getDayOfMonth();
            perDay[day] -= t.getAmountCents();
        }

        List<BalancePointDto> series = new ArrayList<>(daysIn);
        long running = 0L;
        for (int d = 1; d <= daysIn; d++) {
            running += perDay[d];
            series.add(new BalancePointDto(d, MoneyDto.fromCents(running)));
        }
        return series;
    }

    /**
     * Agrega gastos por categoria no período e enriquece com nome
     * (no locale corrente) e cor. Limita a {@value #CATEGORY_DONUT_LIMIT}
     * fatias; o restante vira "outros" agrupado.
     */
    private List<CategoryBreakdownDto> buildCategoryBreakdown(UUID userId,
                                                              LocalDate start,
                                                              LocalDate end) {
        List<Object[]> rows = transactions.aggregateByCategoryAndPeriod(userId, start, end);
        if (rows.isEmpty()) return List.of();

        Map<String, Category> byId = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            byId.put(c.getId(), c);
        }

        Locale locale = LocaleContextHolder.getLocale();
        boolean english = "en".equalsIgnoreCase(locale.getLanguage());

        List<CategoryBreakdownDto> all = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String id = (String) row[0];
            long total = ((Number) row[1]).longValue();
            Category cat = byId.get(id);
            String name = cat == null ? id : (english ? cat.getNameEn() : cat.getNamePt());
            String color = cat == null ? "#666666" : cat.getColorHex();
            all.add(new CategoryBreakdownDto(id, name, color, MoneyDto.fromCents(total)));
        }

        if (all.size() <= CATEGORY_DONUT_LIMIT) return all;

        List<CategoryBreakdownDto> top = new ArrayList<>(all.subList(0, CATEGORY_DONUT_LIMIT));
        long restCents = 0L;
        for (int i = CATEGORY_DONUT_LIMIT; i < all.size(); i++) {
            restCents += all.get(i).amount().movePointRight(2).longValueExact();
        }
        String othersName = english ? "Others" : "Outros";
        top.add(new CategoryBreakdownDto("__others__", othersName, "#94a3b8",
                MoneyDto.fromCents(restCents)));
        return top;
    }
}
