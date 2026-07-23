package com.rastroos.domain.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.SavingsBarView;

/**
 * Agrega os dados da tela "Comparativo": receita vs gasto vs saldo vs aporte
 * ao longo dos últimos 6 meses, a taxa de poupança por mês e os indicadores
 * resumidos (média, meses acima da meta, melhor mês).
 *
 * <p>O aporte estimado do mês é o quanto o saldo dos investimentos cresceu
 * além do rendimento estimado (delta − rendimento, mínimo 0).
 */
@Service
public class CompareService {

    /** Quantos meses no comparativo (mês corrente + 5 anteriores). */
    public static final int TRAILING_MONTHS = 6;

    private final MonthlyFinanceAggregator aggregator;
    private final InvestmentContributionService contributions;

    public CompareService(MonthlyFinanceAggregator aggregator,
                          InvestmentContributionService contributions) {
        this.aggregator = aggregator;
        this.contributions = contributions;
    }

    @Transactional(readOnly = true)
    public CompareModel load(UUID userId, YearMonth ym) {
        Map<String, Long> investedByMonth = contributions.byMonthCents(userId);

        List<MonthSummaryDto> months = new ArrayList<>(TRAILING_MONTHS);
        for (YearMonth m : MonthlyFinanceAggregator.trailingAxis(ym, TRAILING_MONTHS)) {
            long invested = investedByMonth.getOrDefault(m.toString(), 0L);
            months.add(aggregator.summarize(userId, m, invested, m.equals(ym)));
        }

        return withStats(months);
    }

    /** Calcula média, meses acima da meta, melhor mês e a geometria das barras. */
    private CompareModel withStats(List<MonthSummaryDto> months) {
        int target = MonthlyFinanceAggregator.SAVINGS_TARGET_PERCENT;

        int counted = 0;
        long sum = 0L;
        int aboveTarget = 0;
        String bestLabel = null;
        Integer bestRate = null;
        for (MonthSummaryDto m : months) {
            Integer rate = m.savingsRate();
            if (rate == null) continue;
            counted++;
            sum += rate;
            if (rate >= target) aboveTarget++;
            if (bestRate == null || rate > bestRate) {
                bestRate = rate;
                bestLabel = m.label();
            }
        }
        Integer avg = counted > 0 ? (int) Math.round((double) sum / counted) : null;

        return buildBars(months, target, avg, aboveTarget, counted, bestLabel, bestRate);
    }

    /**
     * Deriva a geometria das barras da taxa de poupança. Replica o cálculo do
     * protótipo: o eixo vai de {@code bottom = min(0, menor taxa)} até
     * {@code top = max(target + 10, 40, maior taxa)}, e cada barra é ancorada
     * entre a linha do zero e a sua taxa (podendo cair abaixo do zero).
     */
    private CompareModel buildBars(List<MonthSummaryDto> months, int target, Integer avg,
                                   int aboveTarget, int counted, String bestLabel, Integer bestRate) {
        int maxRate = 0;
        int minRate = 0;
        boolean any = false;
        for (MonthSummaryDto m : months) {
            Integer rate = m.savingsRate();
            if (rate == null) continue;
            maxRate = any ? Math.max(maxRate, rate) : rate;
            minRate = any ? Math.min(minRate, rate) : rate;
            any = true;
        }

        double top = Math.max(Math.max(target + 10.0, 40.0), maxRate);
        double bottom = Math.min(0.0, minRate);
        double range = (top - bottom) == 0 ? 1 : (top - bottom);
        double zeroTop = (top - 0) / range * 100.0;
        double targetTop = (top - target) / range * 100.0;

        List<SavingsBarView> bars = new ArrayList<>(months.size());
        for (MonthSummaryDto m : months) {
            Integer rate = m.savingsRate();
            if (rate == null) {
                bars.add(new SavingsBarView(m.label(), null, zeroTop, 1.5, "muted", false));
                continue;
            }
            double pctFromTop = (top - rate) / range * 100.0;
            double barTop = Math.min(zeroTop, pctFromTop);
            double barBot = Math.max(zeroTop, pctFromTop);
            double height = Math.max(1.5, barBot - barTop);
            String tone = rate >= target ? "ok" : rate >= 0 ? "warn" : "bad";
            bars.add(new SavingsBarView(m.label(), rate, barTop, height, tone, rate < 0));
        }

        return new CompareModel(months, target, avg, aboveTarget, counted, bestLabel, bestRate,
                bars, zeroTop, targetTop);
    }
}
