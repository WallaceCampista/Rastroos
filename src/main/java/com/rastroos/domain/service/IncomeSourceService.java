package com.rastroos.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.IncomeSource;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.IncomeSourceRepository;
import com.rastroos.web.dto.IncomeSourceDto;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.form.IncomeSourceForm;

/**
 * Receita recorrente — o salário fixo de uma empresa cadastrada.
 *
 * <p>Cadastrar uma fonte <b>materializa</b> {@value #HORIZON_YEARS} anos de
 * lançamentos mensais em {@code incomes}, um por mês, apontando para ela. É a
 * mesma estratégia do gasto fixo permanente
 * ({@link TransactionService#PERMANENT_YEARS}) e existe pelo mesmo motivo:
 * dashboard, relatórios e o Alfredo somam uma tabela só, sem precisar
 * reinterpretar uma regra de recorrência em cada consulta.
 *
 * <p>A data de cada mês vem do <b>dia útil</b> combinado, não do dia do
 * calendário: quem recebe no 5º dia útil recebe em datas diferentes a cada mês
 * conforme fim de semana e feriado ({@link BusinessDayCalendar}). E cada
 * recebimento nasce <b>não confirmado</b> — programar não é receber; só o
 * botão "marcar como recebido" faz o valor entrar nos totais de receita.
 *
 * <p>Isolamento estrito por {@code userId}: fonte de outro usuário →
 * {@link ResourceNotFoundException} (404).
 */
@Service
public class IncomeSourceService {

    /** Horizonte materializado de uma receita recorrente. */
    public static final int HORIZON_YEARS = 10;
    static final int HORIZON_MONTHS = HORIZON_YEARS * 12;

    private final IncomeSourceRepository sources;
    private final IncomeRepository incomes;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public IncomeSourceService(IncomeSourceRepository sources,
                               IncomeRepository incomes,
                               Clock clock,
                               ApplicationEventPublisher events) {
        this.sources = sources;
        this.incomes = incomes;
        this.clock = clock;
        this.events = events;
    }

    /** O que fazer com os lançamentos ao remover uma receita recorrente. */
    public enum DeleteScope {
        /** Apaga a fonte e todos os recebimentos dela. */
        ALL,
        /** Apaga só do mês informado em diante e encerra a fonte, preservando o histórico. */
        FROM_MONTH
    }

    @Transactional(readOnly = true)
    public List<IncomeSourceDto> list(UUID userId, YearMonth ym) {
        Map<UUID, Income> occurrences = occurrencesIn(userId, ym);
        return sources.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(s -> toDto(userId, s, ym, occurrences.get(s.getId())))
                .toList();
    }

    /** Só as fontes ativas — as que o formulário de lançamento oferece. */
    @Transactional(readOnly = true)
    public List<IncomeSourceDto> listActive(UUID userId, YearMonth ym) {
        Map<UUID, Income> occurrences = occurrencesIn(userId, ym);
        return sources.findAllByUserIdAndClosedAtIsNullOrderByNameAsc(userId).stream()
                .map(s -> toDto(userId, s, ym, occurrences.get(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public IncomeSourceDto summary(UUID userId, UUID id, YearMonth ym) {
        return toDto(userId, require(userId, id), ym, occurrencesIn(userId, ym).get(id));
    }

    @Transactional(readOnly = true)
    public IncomeSource require(UUID userId, UUID id) {
        return sources.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("incomeSource.notFound"));
    }

    @Transactional
    public IncomeSource create(UUID userId, IncomeSourceForm form) {
        String name = form.getName().trim();
        if (sources.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new IllegalArgumentException("incomeSource.duplicateName");
        }

        IncomeSource s = new IncomeSource();
        s.setUserId(userId);
        s.setName(name);
        s.setAmountCents(amountCentsOf(form));
        s.setPayBusinessDay(form.getPayBusinessDay());
        s.setNote(blankToNull(form.getNote()));
        IncomeSource saved = sources.save(s);

        YearMonth start = form.getStartMonth() != null
                ? form.getStartMonth()
                : YearMonth.now(clock);
        incomes.saveAll(materialize(saved, start, HORIZON_MONTHS));

        dataChanged(userId);
        return saved;
    }

    /**
     * Altera o combinado e reflete nos recebimentos <b>ainda por vir</b>
     * (mês corrente em diante). O que já passou — e o que já foi confirmado
     * como recebido — fica como foi registrado: um aumento de salário não
     * reescreve o holerite do ano passado, nem o depósito que já caiu.
     */
    @Transactional
    public IncomeSource update(UUID userId, UUID id, IncomeSourceForm form) {
        IncomeSource s = require(userId, id);
        String name = form.getName().trim();
        if (sources.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw new IllegalArgumentException("incomeSource.duplicateName");
        }

        s.setName(name);
        s.setAmountCents(amountCentsOf(form));
        s.setPayBusinessDay(form.getPayBusinessDay());
        s.setNote(blankToNull(form.getNote()));
        IncomeSource saved = sources.save(s);

        LocalDate from = YearMonth.now(clock).atDay(1);
        List<Income> future = incomes
                .findAllByUserIdAndSourceIdAndIncomeDateGreaterThanEqualOrderByIncomeDateAsc(
                        userId, id, from);
        List<Income> touched = new ArrayList<>(future.size());
        for (Income i : future) {
            if (i.isReceived()) {
                continue;
            }
            i.setSource(saved.getName());
            i.setAmountCents(saved.getAmountCents());
            i.setIncomeDate(payDate(YearMonth.from(i.getIncomeDate()), saved.getPayBusinessDay()));
            touched.add(i);
        }
        incomes.saveAll(touched);

        dataChanged(userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public long countIncomes(UUID userId, UUID sourceId) {
        return incomes.countByUserIdAndSourceId(userId, sourceId);
    }

    /** Quantos recebimentos da fonte caem no mês informado ou depois dele. */
    @Transactional(readOnly = true)
    public long countIncomesFrom(UUID userId, UUID sourceId, YearMonth ym) {
        return incomes.countByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(
                userId, sourceId, ym.atDay(1));
    }

    /**
     * Remove uma receita recorrente segundo o escopo escolhido — o mesmo
     * contrato de {@link AccountService#delete(UUID, UUID, AccountService.DeleteScope, YearMonth)}.
     *
     * @param ym mês de corte, obrigatório em {@link DeleteScope#FROM_MONTH}
     */
    @Transactional
    public void delete(UUID userId, UUID id, DeleteScope scope, YearMonth ym) {
        IncomeSource s = require(userId, id);
        if (scope == DeleteScope.FROM_MONTH) {
            if (ym == null) {
                throw new IllegalArgumentException("incomeSource.deleteScopeInvalid");
            }
            LocalDate from = ym.atDay(1);
            incomes.deleteByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(userId, id, from);
            // A fonte fica, encerrada no fim do mês anterior: o histórico continua
            // consultável e nada novo é gerado.
            s.setClosedAt(from.minusDays(1));
            sources.save(s);
            dataChanged(userId);
            return;
        }
        incomes.deleteByUserIdAndSourceId(userId, id);
        sources.delete(s);
        dataChanged(userId);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Um recebimento por mês, do mês inicial em diante, com o mesmo valor. */
    private static List<Income> materialize(IncomeSource source, YearMonth start, int months) {
        List<Income> out = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth ym = start.plusMonths(i);
            Income income = new Income();
            income.setUserId(source.getUserId());
            income.setSourceId(source.getId());
            income.setSource(source.getName());
            income.setAmountCents(source.getAmountCents());
            income.setIncomeDate(payDate(ym, source.getPayBusinessDay()));
            // Programado, não recebido: quem confirma é o usuário, no botão.
            income.setReceived(false);
            out.add(income);
        }
        return out;
    }

    /**
     * A data do n-ésimo dia útil naquele mês. Num mês com menos dias úteis do
     * que o combinado, cai no último — não some.
     */
    private static LocalDate payDate(YearMonth ym, short payBusinessDay) {
        return BusinessDayCalendar.nthBusinessDay(ym, payBusinessDay);
    }

    /** Os recebimentos do mês, indexados pela fonte que os gerou. */
    private Map<UUID, Income> occurrencesIn(UUID userId, YearMonth ym) {
        Map<UUID, Income> map = new HashMap<>();
        for (Income i : incomes.findSourceOccurrencesInPeriod(
                userId, ym.atDay(1), ym.plusMonths(1).atDay(1))) {
            map.putIfAbsent(i.getSourceId(), i);
        }
        return map;
    }

    private IncomeSourceDto toDto(UUID userId, IncomeSource s, YearMonth ym, Income occurrence) {
        // Havendo lançamento no mês, a data dele é a verdade (pode ter sido
        // ajustada à mão por um feriado municipal); sem lançamento, projeta-se
        // pelo calendário — a menos que a fonte esteja encerrada.
        LocalDate payDate;
        if (occurrence != null) {
            payDate = occurrence.getIncomeDate();
        } else if (s.getClosedAt() == null) {
            payDate = payDate(ym, s.getPayBusinessDay());
        } else {
            payDate = null;
        }
        return new IncomeSourceDto(
                s.getId(),
                s.getName(),
                MoneyDto.fromCents(s.getAmountCents()),
                s.getPayBusinessDay(),
                s.getNote(),
                s.getClosedAt(),
                incomes.countByUserIdAndSourceId(userId, s.getId()),
                occurrence == null ? null : occurrence.getId(),
                payDate,
                occurrence != null && occurrence.isReceived()
        );
    }

    private static long amountCentsOf(IncomeSourceForm form) {
        long cents = form.getAmount().movePointRight(2).longValueExact();
        if (cents <= 0) {
            throw new IllegalArgumentException("income.amountPositive");
        }
        return cents;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void dataChanged(UUID userId) {
        events.publishEvent(new UserDataChangedEvent(userId));
    }
}
