package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentHistoryEntryDto;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.dto.PortfolioSummaryDto;
import com.rastroos.web.form.InvestmentForm;
import com.rastroos.web.form.InvestmentHistoryForm;

/**
 * Regras de negócio para investimentos: cofrinhos (PIGGY) com metas e
 * carteira (CDB/Tesouro/LCI/etc.) com rendimento mensal estimado.
 *
 * <p>Toda operação verifica ownership por {@code userId} — acesso a id de
 * outro usuário → {@link ResourceNotFoundException} (HTTP 404).
 */
@Service
public class InvestmentService {

    private final InvestmentRepository investments;
    private final InvestmentHistoryRepository history;

    public InvestmentService(InvestmentRepository investments,
                             InvestmentHistoryRepository history) {
        this.investments = investments;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public InvestmentsView load(UUID userId) {
        List<Investment> all = investments.findAllByUserIdOrderByNameAsc(userId);

        List<InvestmentDto> piggies = new ArrayList<>();
        List<InvestmentDto> portfolio = new ArrayList<>();
        for (Investment inv : all) {
            InvestmentDto dto = toDto(inv);
            if (inv.getKind() == InvestmentKind.PIGGY) {
                piggies.add(dto);
            } else {
                portfolio.add(dto);
            }
        }

        long totalCents = investments.sumTotalByUser(userId);
        long goalsCents = investments.sumPiggyGoalsByUser(userId);
        long monthlyReturnCents = investments.sumMonthlyReturnByUser(userId);
        long piggyAmountCents = piggies.stream()
                .mapToLong(p -> p.amount().movePointRight(2).longValueExact())
                .sum();

        Integer piggyProgress = goalsCents > 0
                ? (int) Math.min(100, Math.round(piggyAmountCents * 100.0 / goalsCents))
                : null;

        Map<InvestmentKind, BigDecimal> byKind = new EnumMap<>(InvestmentKind.class);
        for (Object[] row : investments.aggregateByKindAndUser(userId)) {
            InvestmentKind kind = (InvestmentKind) row[0];
            long sum = ((Number) row[1]).longValue();
            byKind.put(kind, MoneyDto.fromCents(sum));
        }

        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                MoneyDto.fromCents(totalCents),
                MoneyDto.fromCents(goalsCents),
                piggyProgress,
                MoneyDto.fromCents(monthlyReturnCents),
                byKind
        );

        return new InvestmentsView(piggies, portfolio, summary);
    }

    @Transactional(readOnly = true)
    public InvestmentDto get(UUID userId, UUID id) {
        return toDto(require(userId, id));
    }

    @Transactional(readOnly = true)
    public Investment require(UUID userId, UUID id) {
        return investments.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("investment.notFound"));
    }

    @Transactional
    public Investment create(UUID userId, InvestmentForm form) {
        Investment inv = new Investment();
        inv.setUserId(userId);
        applyForm(inv, form);
        return investments.save(inv);
    }

    @Transactional
    public Investment update(UUID userId, UUID id, InvestmentForm form) {
        Investment existing = require(userId, id);
        applyForm(existing, form);
        return investments.save(existing);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Investment existing = require(userId, id);
        // remove snapshots de histórico para não deixar órfãos
        List<InvestmentHistory> snapshots = history.findAllByInvestmentIdOrderByYearMonthAsc(id);
        if (!snapshots.isEmpty()) {
            history.deleteAll(snapshots);
        }
        investments.delete(existing);
    }

    @Transactional(readOnly = true)
    public List<InvestmentHistoryEntryDto> getHistory(UUID userId, UUID investmentId) {
        require(userId, investmentId);
        return history.findAllByInvestmentIdOrderByYearMonthAsc(investmentId).stream()
                .map(h -> new InvestmentHistoryEntryDto(
                        h.getYearMonth(),
                        MoneyDto.fromCents(h.getAmountCents())))
                .toList();
    }

    /**
     * Upsert do ponto mensal: se já existir um registro para o {@code yearMonth},
     * atualiza; caso contrário, cria. Atualiza também o {@code amountCents}
     * atual do investimento, para refletir o saldo mais recente.
     */
    @Transactional
    public InvestmentHistory upsertHistory(UUID userId, UUID investmentId,
                                           InvestmentHistoryForm form) {
        Investment inv = require(userId, investmentId);
        long amountCents = form.getAmount().movePointRight(2).longValueExact();
        if (amountCents < 0) {
            throw new IllegalArgumentException("investment.amountPositive");
        }
        InvestmentHistory snapshot = history
                .findByInvestmentIdAndYearMonth(investmentId, form.getYearMonth())
                .orElseGet(() -> {
                    InvestmentHistory h = new InvestmentHistory();
                    h.setInvestmentId(investmentId);
                    h.setYearMonth(form.getYearMonth());
                    return h;
                });
        snapshot.setAmountCents(amountCents);
        history.save(snapshot);

        inv.setAmountCents(amountCents);
        investments.save(inv);
        return snapshot;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static InvestmentDto toDto(Investment inv) {
        BigDecimal amount = MoneyDto.fromCents(inv.getAmountCents());
        BigDecimal goal = inv.getGoalCents() == null
                ? null
                : MoneyDto.fromCents(inv.getGoalCents());

        Integer progress = null;
        if (inv.getGoalCents() != null && inv.getGoalCents() > 0) {
            progress = (int) Math.min(100,
                    Math.round(inv.getAmountCents() * 100.0 / inv.getGoalCents()));
        }
        BigDecimal monthlyReturn = inv.getMonthlyReturnCents() == null
                ? null
                : MoneyDto.fromCents(inv.getMonthlyReturnCents());

        return new InvestmentDto(
                inv.getId(),
                inv.getName(),
                inv.getKind(),
                amount,
                goal,
                progress,
                inv.getRateLabel(),
                monthlyReturn,
                inv.getColorHex(),
                inv.getIconText()
        );
    }

    private static void applyForm(Investment inv, InvestmentForm form) {
        inv.setName(form.getName().trim());
        inv.setKind(form.getKind());
        inv.setAmountCents(toCents(form.getAmount()));
        // goal só faz sentido para PIGGY
        inv.setGoalCents(form.getKind() == InvestmentKind.PIGGY
                ? toCentsOrNull(form.getGoal())
                : null);
        // rate/monthly só para a carteira
        if (form.getKind() == InvestmentKind.PIGGY) {
            inv.setRateLabel(null);
            inv.setMonthlyReturnCents(null);
        } else {
            inv.setRateLabel(blankToNull(form.getRateLabel()));
            inv.setMonthlyReturnCents(toCentsOrNull(form.getMonthlyReturn()));
        }
        inv.setColorHex(blankToNull(form.getColorHex()));
        inv.setIconText(blankToNull(form.getIconText()));
    }

    private static long toCents(BigDecimal value) {
        if (value == null) return 0L;
        return value.movePointRight(2).longValueExact();
    }

    private static Long toCentsOrNull(BigDecimal value) {
        if (value == null) return null;
        return value.movePointRight(2).longValueExact();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
