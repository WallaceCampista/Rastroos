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

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentRepository;

@ExtendWith(MockitoExtension.class)
class InvestmentContributionServiceTest {

    @Mock private InvestmentRepository investments;
    @Mock private InvestmentHistoryRepository history;

    @InjectMocks private InvestmentContributionService service;

    private final UUID alice = UUID.randomUUID();

    @Test
    void byMonthEstimaAporteComoDeltaMenosRendimento() {
        UUID invId = UUID.randomUUID();
        Investment cdb = new Investment();
        cdb.setId(invId);
        cdb.setUserId(alice);
        cdb.setKind(InvestmentKind.CDI);
        cdb.setMonthlyReturnCents(1_000L); // R$10 de rendimento estimado
        when(investments.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(cdb));

        when(history.findAllByUserId(alice)).thenReturn(List.of(
                snapshot(invId, "2026-03", 100_000L),
                snapshot(invId, "2026-04", 105_000L),   // delta 5000 - 1000 = 4000
                snapshot(invId, "2026-05", 112_000L)));  // delta 7000 - 1000 = 6000

        Map<String, Long> byMonth = service.byMonthCents(alice);

        assertThat(byMonth).doesNotContainKey("2026-03"); // primeiro ponto não tem delta
        assertThat(byMonth.get("2026-04")).isEqualTo(4_000L);
        assertThat(byMonth.get("2026-05")).isEqualTo(6_000L);
        assertThat(service.inMonthCents(alice, YearMonth.of(2026, 5))).isEqualTo(6_000L);
        assertThat(service.inMonthCents(alice, YearMonth.of(2026, 3))).isEqualTo(0L);
    }

    @Test
    void byMonthNuncaNegativoQuandoRendimentoSuperaODelta() {
        UUID invId = UUID.randomUUID();
        Investment inv = new Investment();
        inv.setId(invId);
        inv.setUserId(alice);
        inv.setKind(InvestmentKind.CDI);
        inv.setMonthlyReturnCents(10_000L); // rendimento maior que o delta
        when(investments.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(inv));

        when(history.findAllByUserId(alice)).thenReturn(List.of(
                snapshot(invId, "2026-04", 100_000L),
                snapshot(invId, "2026-05", 102_000L))); // delta 2000 - 10000 → max(0, .) = 0

        assertThat(service.inMonthCents(alice, YearMonth.of(2026, 5))).isEqualTo(0L);
    }

    private static InvestmentHistory snapshot(UUID investmentId, String ym, long cents) {
        InvestmentHistory h = new InvestmentHistory();
        h.setInvestmentId(investmentId);
        h.setYearMonth(ym);
        h.setAmountCents(cents);
        return h;
    }
}
