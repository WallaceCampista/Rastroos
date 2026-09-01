package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.repository.InvestmentHistoryRepository;

@ExtendWith(MockitoExtension.class)
class InvestmentContributionServiceTest {

    @Mock private InvestmentHistoryRepository history;

    @InjectMocks private InvestmentContributionService service;

    private final UUID alice = UUID.randomUUID();

    @Test
    void byMonthSomaContributedCentsPorMes() {
        UUID invId = UUID.randomUUID();
        when(history.findAllByUserId(alice)).thenReturn(List.of(
                snapshot(invId, "2026-03", 100_000L, 100_000L),  // criação: aporte 1000,00
                snapshot(invId, "2026-04", 105_000L, 5_000L),    // aporte 50,00
                snapshot(invId, "2026-05", 112_000L, 7_000L)));  // aporte 70,00

        Map<String, Long> byMonth = service.byMonthCents(alice);

        assertThat(byMonth.get("2026-03")).isEqualTo(100_000L);
        assertThat(byMonth.get("2026-04")).isEqualTo(5_000L);
        assertThat(byMonth.get("2026-05")).isEqualTo(7_000L);
        assertThat(service.inMonthCents(alice, YearMonth.of(2026, 5))).isEqualTo(7_000L);
    }

    @Test
    void byMonthSomaAportesDeVariosInvestimentosNoMesmoMes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(history.findAllByUserId(alice)).thenReturn(List.of(
                snapshot(a, "2026-05", 30_000L, 30_000L),
                snapshot(b, "2026-05", 20_000L, 20_000L)));

        assertThat(service.inMonthCents(alice, YearMonth.of(2026, 5))).isEqualTo(50_000L);
    }

    @Test
    void byMonthIgnoraMesSemAporte() {
        UUID invId = UUID.randomUUID();
        when(history.findAllByUserId(alice)).thenReturn(List.of(
                snapshot(invId, "2026-04", 100_000L, 0L)));  // só rendimento, sem dinheiro novo

        assertThat(service.byMonthCents(alice)).doesNotContainKey("2026-04");
        assertThat(service.inMonthCents(alice, YearMonth.of(2026, 4))).isEqualTo(0L);
    }

    private static InvestmentHistory snapshot(UUID investmentId, String ym, long cents, long contributed) {
        InvestmentHistory h = new InvestmentHistory();
        h.setInvestmentId(investmentId);
        h.setYearMonth(ym);
        h.setAmountCents(cents);
        h.setContributedCents(contributed);
        return h;
    }
}
