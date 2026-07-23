package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.rastroos.web.dto.MonthChipView;
import com.rastroos.web.dto.MonthSummaryDto;

/**
 * Monta os 12 chips de mês do seletor de período (topbar), classificando cada
 * mês do ano vigente por status financeiro. Espelha a lógica de
 * {@code monthStatus} do protótipo:
 *
 * <ul>
 *   <li>{@code future}   — mês futuro que já tem lançamentos (projeção);</li>
 *   <li>{@code empty}    — sem receitas nem gastos;</li>
 *   <li>{@code negative} — saldo previsto ({@code net}) negativo;</li>
 *   <li>{@code paid}     — tudo pago ({@code toPay ≈ 0}) e houve gasto;</li>
 *   <li>{@code pending}  — há saldo a pagar.</li>
 * </ul>
 */
@Service
public class PeriodChipsService {

    private static final BigDecimal PAID_EPSILON = new BigDecimal("0.005");

    private final MonthlyFinanceAggregator aggregator;
    private final Clock clock;

    public PeriodChipsService(MonthlyFinanceAggregator aggregator, Clock clock) {
        this.aggregator = aggregator;
        this.clock = clock;
    }

    /** Sequência de meses no/fora do controle (relativo ao mês corrente). */
    public record StreakView(int inControl, int outControl) {
    }

    public StreakView streak(UUID userId) {
        YearMonth k = YearMonth.now(clock);
        MonthSummaryDto start = aggregator.summarize(userId, k, 0L, false);
        // Se o mês corrente ainda não fechou (falta pagar) ou está sem gastos,
        // começa do mês anterior — não penaliza o mês em andamento.
        if (start.toPay().compareTo(PAID_EPSILON) > 0 || start.spent().signum() == 0) {
            k = k.minusMonths(1);
        }
        int inControl = 0;
        int outControl = 0;
        for (int i = 0; i < 12; i++) {
            MonthSummaryDto s = aggregator.summarize(userId, k, 0L, false);
            if (s.spent().signum() == 0) {
                break;
            }
            boolean ok = s.toPay().compareTo(PAID_EPSILON) <= 0 && s.net().signum() >= 0;
            if (ok) {
                inControl++;
            } else {
                outControl++;
            }
            k = k.minusMonths(1);
        }
        return new StreakView(inControl, outControl);
    }

    public List<MonthChipView> yearChips(UUID userId, YearMonth selected) {
        int year = selected.getYear();
        YearMonth today = YearMonth.now(clock);
        Locale locale = LocaleContextHolder.getLocale();

        List<MonthChipView> chips = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            MonthSummaryDto summary = aggregator.summarize(userId, ym, 0L, ym.equals(selected));
            chips.add(new MonthChipView(
                    ym.toString(),
                    monthLabel(ym, locale),
                    status(summary, ym, today),
                    ym.equals(selected)));
        }
        return chips;
    }

    private String status(MonthSummaryDto s, YearMonth ym, YearMonth today) {
        boolean hasData = s.received().signum() != 0 || s.spent().signum() != 0;
        if (ym.isAfter(today)) {
            return hasData ? "future" : "empty";
        }
        if (!hasData) {
            return "empty";
        }
        if (s.net().signum() < 0) {
            return "negative";
        }
        if (s.toPay().compareTo(PAID_EPSILON) <= 0 && s.spent().signum() > 0) {
            return "paid";
        }
        return "pending";
    }

    private String monthLabel(YearMonth ym, Locale locale) {
        String label = ym.getMonth().getDisplayName(TextStyle.SHORT, locale).replace(".", "");
        if (label.isEmpty()) {
            return label;
        }
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
