package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.MoneyDto;

@ExtendWith(MockitoExtension.class)
class CompareServiceTest {

    @Mock private MonthlyFinanceAggregator aggregator;
    @Mock private InvestmentContributionService contributions;

    @InjectMocks private CompareService service;

    private final UUID alice = UUID.randomUUID();

    @Test
    void loadRepassaOAporteMensalParaOAgregador() {
        // aggregator ecoa o investedCents recebido para dentro do DTO
        when(aggregator.summarize(eq(alice), any(YearMonth.class), anyLong(), anyBoolean()))
                .thenAnswer(inv -> echoInvested((YearMonth) inv.getArgument(1),
                        (long) inv.getArgument(2), (boolean) inv.getArgument(3)));

        when(contributions.byMonthCents(alice))
                .thenReturn(Map.of("2026-04", 4_000L, "2026-05", 6_000L));

        CompareModel data = service.load(alice, YearMonth.of(2026, 5));

        assertThat(data.months()).hasSize(6);
        assertThat(investedFor(data, "2026-03").toPlainString()).isEqualTo("0.00");
        assertThat(investedFor(data, "2026-04").toPlainString()).isEqualTo("40.00");
        assertThat(investedFor(data, "2026-05").toPlainString()).isEqualTo("60.00");
    }

    @Test
    void loadCalculaEstatisticasDeTaxaDePoupanca() {
        Map<String, Integer> rates = Map.of(
                "2026-01", 10,
                "2026-02", 25,
                "2026-03", 30,
                "2026-05", 22);
        when(aggregator.summarize(eq(alice), any(YearMonth.class), anyLong(), anyBoolean()))
                .thenAnswer(inv -> withRate((YearMonth) inv.getArgument(1),
                        rates::get, (boolean) inv.getArgument(3)));

        when(contributions.byMonthCents(alice)).thenReturn(Map.of());

        CompareModel data = service.load(alice, YearMonth.of(2026, 5));

        assertThat(data.target()).isEqualTo(20);
        assertThat(data.monthsCounted()).isEqualTo(4);       // jan, fev, mar, mai
        assertThat(data.monthsAboveTarget()).isEqualTo(3);   // 25, 30, 22
        assertThat(data.avgSavingsRate()).isEqualTo(22);     // round(87/4)
        assertThat(data.bestMonthLabel()).isEqualTo("2026-03");
        assertThat(data.bestMonthRate()).isEqualTo(30);
    }

    // ── helpers ──────────────────────────────────────────────

    private static BigDecimal investedFor(CompareModel data, String ym) {
        return data.months().stream()
                .filter(m -> m.yearMonth().equals(ym))
                .findFirst()
                .orElseThrow()
                .invested();
    }

    private static MonthSummaryDto echoInvested(YearMonth ym, long investedCents, boolean current) {
        BigDecimal z = new BigDecimal("0.00");
        return new MonthSummaryDto(ym.toString(), ym.toString(),
                z, z, z, z, z, z, z, MoneyDto.fromCents(investedCents), null, current);
    }

    private static MonthSummaryDto withRate(YearMonth ym, Function<String, Integer> rateFn,
                                            boolean current) {
        BigDecimal z = new BigDecimal("0.00");
        Integer rate = rateFn.apply(ym.toString());
        return new MonthSummaryDto(ym.toString(), ym.toString(),
                z, z, z, z, z, z, z, z, rate, current);
    }
}
