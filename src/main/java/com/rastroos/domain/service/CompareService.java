package com.rastroos.domain.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.MonthSummaryDto;

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
    private final InvestmentRepository investments;
    private final InvestmentHistoryRepository history;

    public CompareService(MonthlyFinanceAggregator aggregator,
                          InvestmentRepository investments,
                          InvestmentHistoryRepository history) {
        this.aggregator = aggregator;
        this.investments = investments;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public CompareModel load(UUID userId, YearMonth ym) {
        Map<String, Long> investedByMonth = investedByMonth(userId);

        List<MonthSummaryDto> months = new ArrayList<>(TRAILING_MONTHS);
        for (YearMonth m : MonthlyFinanceAggregator.trailingAxis(ym, TRAILING_MONTHS)) {
            long invested = investedByMonth.getOrDefault(m.toString(), 0L);
            months.add(aggregator.summarize(userId, m, invested, m.equals(ym)));
        }

        return withStats(months);
    }

    /**
     * Estima o aporte por mês: para cada investimento, percorre os snapshots
     * em ordem cronológica e soma {@code max(0, delta − rendimentoMensal)} no
     * mês do snapshot mais recente do par. Chave do mapa: {@code "YYYY-MM"}.
     */
    private Map<String, Long> investedByMonth(UUID userId) {
        Map<UUID, Long> monthlyReturn = new HashMap<>();
        for (Investment inv : investments.findAllByUserIdOrderByNameAsc(userId)) {
            long ret = inv.getMonthlyReturnCents() == null ? 0L : inv.getMonthlyReturnCents();
            monthlyReturn.put(inv.getId(), ret);
        }

        Map<String, Long> byMonth = new HashMap<>();
        UUID lastInvestment = null;
        long lastAmount = 0L;
        // findAllByUserId vem ordenado por (investmentId, yearMonth)
        for (InvestmentHistory h : history.findAllByUserId(userId)) {
            if (!h.getInvestmentId().equals(lastInvestment)) {
                lastInvestment = h.getInvestmentId();
                lastAmount = h.getAmountCents();
                continue; // primeiro ponto do investimento não tem delta
            }
            long delta = h.getAmountCents() - lastAmount;
            long contribution = Math.max(0L, delta - monthlyReturn.getOrDefault(lastInvestment, 0L));
            byMonth.merge(h.getYearMonth(), contribution, Long::sum);
            lastAmount = h.getAmountCents();
        }
        return byMonth;
    }

    /** Calcula média, meses acima da meta e melhor mês a partir das taxas. */
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

        return new CompareModel(months, target, avg, aboveTarget, counted, bestLabel, bestRate);
    }
}
