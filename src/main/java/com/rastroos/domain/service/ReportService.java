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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.dto.ReportsModel;

/**
 * Agrega os dados da tela "Relatórios" do mês selecionado: gastos por
 * categoria e por conta, pago vs a pagar, fixo vs pontual e a evolução
 * fixo/variável nos últimos 6 meses.
 *
 * <p>Tudo filtrado por {@code userId}; o controller só repassa o id.
 */
@Service
public class ReportService {

    /** Quantos meses na linha de evolução (mês corrente + 5 anteriores). */
    public static final int TRAILING_MONTHS = 6;

    /** Cor de fallback para conta/categoria sem cor definida. */
    private static final String FALLBACK_COLOR = "#6366f1";

    private final MonthlyFinanceAggregator aggregator;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final AccountRepository accounts;

    public ReportService(MonthlyFinanceAggregator aggregator,
                         TransactionRepository transactions,
                         CategoryRepository categories,
                         AccountRepository accounts) {
        this.aggregator = aggregator;
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public ReportsModel load(UUID userId, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        MonthSummaryDto current = aggregator.summarize(userId, ym, 0L, true);
        List<CategoryBreakdownDto> byCategory = buildCategoryBreakdown(userId, start, end);
        List<CategoryBreakdownDto> byAccount = buildAccountBreakdown(userId, start, end);

        List<MonthSummaryDto> trailing6 = new ArrayList<>(TRAILING_MONTHS);
        for (YearMonth m : MonthlyFinanceAggregator.trailingAxis(ym, TRAILING_MONTHS)) {
            trailing6.add(aggregator.summarize(userId, m, 0L, m.equals(ym)));
        }

        return new ReportsModel(start, end, current, byCategory, byAccount, trailing6);
    }

    /**
     * Gastos por categoria no período, enriquecidos com nome (no locale) e
     * cor. Ordenados desc por valor; sem cortar (a tela mostra a legenda toda).
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
        boolean english = isEnglish();

        List<CategoryBreakdownDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String id = (String) row[0];
            long total = ((Number) row[1]).longValue();
            Category cat = byId.get(id);
            String name = cat == null ? id : (english ? cat.getNameEn() : cat.getNamePt());
            String color = cat == null ? FALLBACK_COLOR : cat.getColorHex();
            result.add(new CategoryBreakdownDto(id, name, color, MoneyDto.fromCents(total)));
        }
        result.sort((x, y) -> y.amount().compareTo(x.amount()));
        return result;
    }

    /**
     * Gastos por conta/cartão no período. Usa a agregação
     * {@code [accountId, total, paid, count]} e reusa
     * {@link CategoryBreakdownDto} (id como texto). Ordenado desc por total,
     * contas sem gasto no mês ficam de fora.
     */
    private List<CategoryBreakdownDto> buildAccountBreakdown(UUID userId,
                                                             LocalDate start,
                                                             LocalDate end) {
        List<Object[]> rows = transactions.aggregateByAccountAndPeriod(userId, start, end);
        if (rows.isEmpty()) return List.of();

        Map<UUID, Account> byId = new HashMap<>();
        for (Account a : accounts.findAllByUserIdOrderByNameAsc(userId)) {
            byId.put(a.getId(), a);
        }

        List<CategoryBreakdownDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID accountId = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            if (total <= 0) continue;
            Account acc = byId.get(accountId);
            String name = acc == null ? "—" : acc.getName();
            String color = (acc == null || acc.getColorHex() == null)
                    ? FALLBACK_COLOR : acc.getColorHex();
            result.add(new CategoryBreakdownDto(
                    accountId.toString(), name, color, MoneyDto.fromCents(total)));
        }
        result.sort((x, y) -> y.amount().compareTo(x.amount()));
        return result;
    }

    private static boolean isEnglish() {
        Locale locale = LocaleContextHolder.getLocale();
        return "en".equalsIgnoreCase(locale.getLanguage());
    }
}
