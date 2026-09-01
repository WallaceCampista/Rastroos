package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.InvestmentMovement;
import com.rastroos.domain.entity.enums.InvestmentMovementKind;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentMovementRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.web.dto.InvestmentChartData;
import com.rastroos.web.dto.InvestmentDetailView;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentHistoryEntryDto;
import com.rastroos.web.dto.InvestmentMovementDto;
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

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private final InvestmentRepository investments;
    private final InvestmentHistoryRepository history;
    private final InvestmentMovementRepository movements;

    public InvestmentService(InvestmentRepository investments,
                             InvestmentHistoryRepository history,
                             InvestmentMovementRepository movements) {
        this.investments = investments;
        this.history = history;
        this.movements = movements;
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

        InvestmentChartData chart = buildChart(userId, all);
        return new InvestmentsView(piggies, portfolio, summary, chart);
    }

    private static final String[] MONTHS_PT = {
            "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
            "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };

    /**
     * Séries dos últimos 6 meses: patrimônio total por mês (onda do hero) e
     * o histórico de cada investimento (sparkline). Usa carry-forward — o
     * valor de um mês sem snapshot herda o último snapshot anterior; antes do
     * primeiro snapshot vale 0; investimentos sem histórico usam o saldo atual.
     */
    private InvestmentChartData buildChart(UUID userId, List<Investment> all) {
        YearMonth end = YearMonth.now();
        List<YearMonth> months = new ArrayList<>(6);
        for (int i = 5; i >= 0; i--) months.add(end.minusMonths(i));

        List<String> labels = months.stream()
                .map(m -> MONTHS_PT[m.getMonthValue() - 1])
                .toList();

        Map<UUID, List<InvestmentHistory>> byInv = new HashMap<>();
        for (InvestmentHistory h : history.findAllByUserId(userId)) {
            byInv.computeIfAbsent(h.getInvestmentId(), k -> new ArrayList<>()).add(h);
        }

        long[] total = new long[months.size()];
        Map<String, List<Long>> sparklines = new LinkedHashMap<>();
        for (Investment inv : all) {
            List<InvestmentHistory> h = byInv.getOrDefault(inv.getId(), List.of());
            List<Long> series = new ArrayList<>(months.size());
            for (int i = 0; i < months.size(); i++) {
                long val = valueAtMonth(h, months.get(i).toString(), inv.getAmountCents());
                series.add(val);
                total[i] += val;
            }
            sparklines.put(String.valueOf(inv.getId()), series);
        }

        List<Long> totalList = new ArrayList<>(months.size());
        for (long v : total) totalList.add(v);
        return new InvestmentChartData(labels, totalList, sparklines);
    }

    /** Valor (centavos) de um investimento no mês, com carry-forward. */
    private static long valueAtMonth(List<InvestmentHistory> hist, String monthKey, long currentCents) {
        if (hist.isEmpty()) return currentCents;   // sem histórico → saldo atual
        long value = 0L;
        boolean any = false;
        for (InvestmentHistory e : hist) {         // ordenado asc por yearMonth
            if (e.getYearMonth().compareTo(monthKey) <= 0) { value = e.getAmountCents(); any = true; }
            else break;
        }
        return any ? value : 0L;                   // antes do 1º snapshot → 0
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
        Investment saved = investments.save(inv);

        // Saldo inicial conta como aporte do mês da criação (dinheiro novo).
        if (saved.getAmountCents() > 0) {
            InvestmentHistory snap = new InvestmentHistory();
            snap.setInvestmentId(saved.getId());
            snap.setYearMonth(YearMonth.now().toString());
            snap.setAmountCents(saved.getAmountCents());
            snap.setContributedCents(saved.getAmountCents());
            history.save(snap);
            movements.save(new InvestmentMovement(saved.getId(), InvestmentMovementKind.INITIAL,
                    saved.getAmountCents(), saved.getAmountCents(), Instant.now()));
        }
        return saved;
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
        movements.deleteByInvestmentId(id);
        investments.delete(existing);
    }

    /** Aporte num investimento existente: soma ao saldo e registra o snapshot do mês. */
    @Transactional
    public Investment deposit(UUID userId, UUID id, BigDecimal addAmount) {
        Investment inv = require(userId, id);
        long addCents = addAmount == null ? 0L : addAmount.movePointRight(2).longValueExact();
        if (addCents <= 0) {
            throw new IllegalArgumentException("investment.amountPositive");
        }
        long total = inv.getAmountCents() + addCents;
        inv.setAmountCents(total);
        investments.save(inv);

        String ym = YearMonth.now().toString();
        InvestmentHistory snap = history.findByInvestmentIdAndYearMonth(id, ym)
                .orElseGet(() -> {
                    InvestmentHistory h = new InvestmentHistory();
                    h.setInvestmentId(id);
                    h.setYearMonth(ym);
                    return h;
                });
        snap.setAmountCents(total);
        snap.setContributedCents(snap.getContributedCents() + addCents);   // dinheiro novo do mês
        history.save(snap);
        movements.save(new InvestmentMovement(id, InvestmentMovementKind.DEPOSIT,
                addCents, total, Instant.now()));
        return inv;
    }

    /** Resgate de um investimento existente: subtrai do saldo e registra o snapshot do mês. */
    @Transactional
    public Investment withdraw(UUID userId, UUID id, BigDecimal amount) {
        Investment inv = require(userId, id);
        long cents = amount == null ? 0L : amount.movePointRight(2).longValueExact();
        if (cents <= 0) {
            throw new IllegalArgumentException("investment.amountPositive");
        }
        if (cents > inv.getAmountCents()) {
            throw new IllegalArgumentException("investment.amountOverBalance");
        }
        long total = inv.getAmountCents() - cents;
        inv.setAmountCents(total);
        investments.save(inv);

        String ym = YearMonth.now().toString();
        InvestmentHistory snap = history.findByInvestmentIdAndYearMonth(id, ym)
                .orElseGet(() -> {
                    InvestmentHistory h = new InvestmentHistory();
                    h.setInvestmentId(id);
                    h.setYearMonth(ym);
                    return h;
                });
        snap.setAmountCents(total);
        history.save(snap);
        movements.save(new InvestmentMovement(id, InvestmentMovementKind.WITHDRAW,
                cents, total, Instant.now()));
        return inv;
    }

    /** Detalhe do investimento com as movimentações do livro-razão (mais recentes primeiro). */
    @Transactional(readOnly = true)
    public InvestmentDetailView investmentDetail(UUID userId, UUID id) {
        Investment inv = require(userId, id);
        List<InvestmentMovementDto> moves = new ArrayList<>();
        for (InvestmentMovement m : movements.findAllByInvestmentIdOrderByOccurredAtDescIdDesc(id)) {
            moves.add(new InvestmentMovementDto(
                    DAY_FMT.format(m.getOccurredAt()),
                    m.getKind().name(),
                    MoneyDto.fromCents(m.getAmountCents()),
                    MoneyDto.fromCents(m.getBalanceAfterCents())));
        }
        return new InvestmentDetailView(toDto(inv), moves);
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
        // Taxa/rendimento valem para todos (inclusive cofrinho, como no protótipo).
        inv.setRateLabel(blankToNull(form.getRateLabel()));
        inv.setMonthlyReturnCents(toCentsOrNull(form.getMonthlyReturn()));
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
