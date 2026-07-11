package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.MoneyDto;

@ExtendWith(MockitoExtension.class)
class CompareServiceTest {

    @Mock private MonthlyFinanceAggregator aggregator;
    @Mock private InvestmentRepository investments;
    @Mock private InvestmentHistoryRepository history;

    @InjectMocks private CompareService service;

    private final UUID alice = UUID.randomUUID();

    @Test
    void loadEstimaAporteMensalComoDeltaMenosRendimento() {
        // aggregator ecoa o investedCents recebido para dentro do DTO
        when(aggregator.summarize(eq(alice), any(YearMonth.class), anyLong(), anyBoolean()))
                .thenAnswer(inv -> echoInvested((YearMonth) inv.getArgument(1),
                        (long) inv.getArgument(2), (boolean) inv.getArgument(3)));

        UUID invId = UUID.randomUUID();
        Investment cdb = new Investment();
        cdb.setId(invId);
        cdb.setUserId(alice);
        cdb.setKind(InvestmentKind.CDI);
        cdb.setMonthlyReturnCents(1_000L); // R$10 de rendimento estimado
        when(investments.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(cdb));

        when(history.findAllByUserId(alice)).thenReturn(List.of(
                snapshot(invId, "2026-03", 100_000L),
                snapshot(invId, "2026-04", 105_000L),  // delta 5000 - 1000 = 4000
                snapshot(invId, "2026-05", 112_000L))); // delta 7000 - 1000 = 6000

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

        when(investments.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of());
        when(history.findAllByUserId(alice)).thenReturn(List.of());

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

    private static InvestmentHistory snapshot(UUID investmentId, String ym, long cents) {
        InvestmentHistory h = new InvestmentHistory();
        h.setInvestmentId(investmentId);
        h.setYearMonth(ym);
        h.setAmountCents(cents);
        return h;
    }
}
