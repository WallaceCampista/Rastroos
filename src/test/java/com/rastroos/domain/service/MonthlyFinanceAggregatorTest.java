package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.MonthSummaryDto;

@ExtendWith(MockitoExtension.class)
class MonthlyFinanceAggregatorTest {

    @Mock private TransactionRepository txRepo;
    @Mock private IncomeRepository incomeRepo;

    @InjectMocks private MonthlyFinanceAggregator aggregator;

    private final UUID alice = UUID.randomUUID();

    @Test
    void summarizeCalculaTodosOsCamposDerivados() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);

        when(txRepo.aggregateTotalsByPeriod(alice, start, end))
                .thenReturn(List.<Object[]>of(new Object[] { 300_000L, 100_000L, 120_000L }));
        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(500_000L);

        MonthSummaryDto s = aggregator.summarize(alice, ym, 42_000L, true);

        assertThat(s.yearMonth()).isEqualTo("2026-05");
        assertThat(s.received().toPlainString()).isEqualTo("5000.00");
        assertThat(s.spent().toPlainString()).isEqualTo("3000.00");
        assertThat(s.paid().toPlainString()).isEqualTo("1000.00");
        assertThat(s.toPay().toPlainString()).isEqualTo("2000.00");
        assertThat(s.fixed().toPlainString()).isEqualTo("1200.00");
        assertThat(s.oneTime().toPlainString()).isEqualTo("1800.00");
        assertThat(s.net().toPlainString()).isEqualTo("2000.00");
        assertThat(s.invested().toPlainString()).isEqualTo("420.00");
        // (500000 - 300000) / 500000 = 40%
        assertThat(s.savingsRate()).isEqualTo(40);
        assertThat(s.current()).isTrue();
    }

    @Test
    void savingsRateNuloQuandoNaoHaReceita() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        when(txRepo.aggregateTotalsByPeriod(alice, start, end))
                .thenReturn(List.<Object[]>of(new Object[] { 10_000L, 0L, 0L }));
        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);

        MonthSummaryDto s = aggregator.summarize(alice, ym, 0L, false);

        assertThat(s.savingsRate()).isNull();
        assertThat(s.net().toPlainString()).isEqualTo("-100.00");
        assertThat(s.current()).isFalse();
    }

    @Test
    void toPayEOneTimeNuncaNegativos() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        // paid > spent e fixed > spent (dados inconsistentes) → clamp em 0
        when(txRepo.aggregateTotalsByPeriod(alice, start, end))
                .thenReturn(List.<Object[]>of(new Object[] { 1_000L, 1_500L, 2_000L }));
        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);

        MonthSummaryDto s = aggregator.summarize(alice, ym, 0L, false);

        assertThat(s.toPay().toPlainString()).isEqualTo("0.00");
        assertThat(s.oneTime().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void aggregateVazioTratadoComoZeros() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        when(txRepo.aggregateTotalsByPeriod(alice, start, end)).thenReturn(List.of());
        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);

        MonthSummaryDto s = aggregator.summarize(alice, ym, 0L, true);

        assertThat(s.spent().toPlainString()).isEqualTo("0.00");
        assertThat(s.paid().toPlainString()).isEqualTo("0.00");
        assertThat(s.fixed().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void trailingAxisRetornaMesesEmOrdemCronologica() {
        List<YearMonth> axis = MonthlyFinanceAggregator.trailingAxis(YearMonth.of(2026, 5), 6);

        assertThat(axis).hasSize(6);
        assertThat(axis.get(0)).isEqualTo(YearMonth.of(2025, 12));
        assertThat(axis.get(5)).isEqualTo(YearMonth.of(2026, 5));
    }
}
