package com.rastroos.domain.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.MoneyDto;

/**
 * Monta o {@link MonthSummaryDto} de um mês a partir dos repositórios de
 * transações e receitas. Compartilhado por {@link ReportService} e
 * {@link CompareService} para não duplicar a agregação de "recebido, gasto,
 * pago, fixo, pontual, saldo".
 *
 * <p>Sempre filtra por {@code userId} — nenhuma leitura cruza usuários.
 */
@Component
public class MonthlyFinanceAggregator {

    /** Meta de poupança padrão (% da receita), usada pela taxa de poupança. */
    public static final int SAVINGS_TARGET_PERCENT = 20;

    private final TransactionRepository transactions;
    private final IncomeRepository incomes;

    public MonthlyFinanceAggregator(TransactionRepository transactions,
                                    IncomeRepository incomes) {
        this.transactions = transactions;
        this.incomes = incomes;
    }

    /**
     * Resume um mês. {@code investedCents} é calculado por quem chama (o
     * comparativo estima aporte; o relatório passa 0). {@code current} marca
     * o mês selecionado para destaque na UI.
     */
    public MonthSummaryDto summarize(UUID userId, YearMonth ym,
                                     long investedCents, boolean current) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        List<Object[]> agg = transactions.aggregateTotalsByPeriod(userId, start, end);
        Object[] row = agg.isEmpty() ? new Object[] { 0L, 0L, 0L } : agg.get(0);
        long spent = toLong(row[0]);
        long paid = toLong(row[1]);
        long fixed = toLong(row[2]);
        long received = incomes.sumAmountByUserAndPeriod(userId, start, end);

        long toPay = Math.max(0L, spent - paid);
        long oneTime = Math.max(0L, spent - fixed);
        long net = received - spent;

        Integer savingsRate = received > 0
                ? (int) Math.round((received - spent) * 100.0 / received)
                : null;

        return new MonthSummaryDto(
                ym.toString(),
                shortLabel(ym),
                MoneyDto.fromCents(received),
                MoneyDto.fromCents(spent),
                MoneyDto.fromCents(paid),
                MoneyDto.fromCents(toPay),
                MoneyDto.fromCents(fixed),
                MoneyDto.fromCents(oneTime),
                MoneyDto.fromCents(net),
                MoneyDto.fromCents(investedCents),
                savingsRate,
                current
        );
    }

    /**
     * Eixo de {@code count} meses terminando em {@code end} (inclusive),
     * em ordem cronológica. Ex.: {@code (2026-05, 6)} → Dez..Mai.
     */
    public static List<YearMonth> trailingAxis(YearMonth end, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> end.minusMonths((long) count - 1 - i))
                .toList();
    }

    /** Rótulo curto do mês no locale corrente, sem ponto e capitalizado. */
    private static String shortLabel(YearMonth ym) {
        Locale locale = LocaleContextHolder.getLocale();
        String raw = ym.getMonth().getDisplayName(TextStyle.SHORT, locale).replace(".", "");
        if (raw.isEmpty()) return raw;
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
