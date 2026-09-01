package com.rastroos.domain.service;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.repository.InvestmentHistoryRepository;

/**
 * Aporte (dinheiro novo investido) por mês: soma {@code contributed_cents} dos
 * snapshots de {@code investment_history} — o valor exato aportado em cada mês,
 * registrado na criação e em cada aporte. Não é mais estimado por delta de saldo,
 * então aportes no mesmo mês (ou no 1º mês do investimento) contam corretamente.
 *
 * <p>Compartilhado por Comparativo (série de 6 meses) e Visão geral (mês corrente),
 * para não duplicar a regra.
 */
@Service
public class InvestmentContributionService {

    private final InvestmentHistoryRepository history;

    public InvestmentContributionService(InvestmentHistoryRepository history) {
        this.history = history;
    }

    /** Aporte (dinheiro novo) por mês ({@code "YYYY-MM"} → centavos). */
    @Transactional(readOnly = true)
    public Map<String, Long> byMonthCents(UUID userId) {
        Map<String, Long> byMonth = new HashMap<>();
        for (InvestmentHistory h : history.findAllByUserId(userId)) {
            long contributed = h.getContributedCents();
            if (contributed > 0) {
                byMonth.merge(h.getYearMonth(), contributed, Long::sum);
            }
        }
        return byMonth;
    }

    /** Aporte de um único mês (centavos). */
    @Transactional(readOnly = true)
    public long inMonthCents(UUID userId, YearMonth ym) {
        return byMonthCents(userId).getOrDefault(ym.toString(), 0L);
    }
}
