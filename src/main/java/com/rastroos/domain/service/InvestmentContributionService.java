package com.rastroos.domain.service;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentRepository;

/**
 * Estima o aporte (contribuição) por mês a partir do histórico de investimentos:
 * para cada investimento, percorre os snapshots em ordem cronológica e soma
 * {@code max(0, delta − rendimentoMensal)} no mês do snapshot mais recente do par.
 *
 * <p>Compartilhado por Comparativo (série de 6 meses) e Visão geral (mês corrente),
 * para não duplicar a regra.
 */
@Service
public class InvestmentContributionService {

    private final InvestmentRepository investments;
    private final InvestmentHistoryRepository history;

    public InvestmentContributionService(InvestmentRepository investments,
                                         InvestmentHistoryRepository history) {
        this.investments = investments;
        this.history = history;
    }

    /** Aporte estimado por mês ({@code "YYYY-MM"} → centavos). */
    @Transactional(readOnly = true)
    public Map<String, Long> byMonthCents(UUID userId) {
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

    /** Aporte estimado de um único mês (centavos). */
    @Transactional(readOnly = true)
    public long inMonthCents(UUID userId, YearMonth ym) {
        return byMonthCents(userId).getOrDefault(ym.toString(), 0L);
    }
}
