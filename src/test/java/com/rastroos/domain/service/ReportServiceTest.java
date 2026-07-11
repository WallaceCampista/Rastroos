package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.ReportsModel;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private MonthlyFinanceAggregator aggregator;
    @Mock private TransactionRepository txRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private AccountRepository accountRepo;

    @InjectMocks private ReportService service;

    private final UUID alice = UUID.randomUUID();

    private void stubAggregator() {
        when(aggregator.summarize(eq(alice), any(YearMonth.class), anyLong(), anyBoolean()))
                .thenAnswer(inv -> summary((YearMonth) inv.getArgument(1),
                        (boolean) inv.getArgument(3)));
    }

    @Test
    void loadEnriqueceCategoriasComNomeECor() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        stubAggregator();

        Category moradia = category("moradia", "Moradia", "Housing", "#abcdef");
        Category lazer = category("lazer", "Lazer", "Leisure", "#123456");
        when(categoryRepo.findAllByOrderBySortOrderAsc()).thenReturn(List.of(moradia, lazer));

        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.<Object[]>of(
                new Object[] { "moradia", 50_000L },
                new Object[] { "lazer", 8_000L }));
        when(txRepo.aggregateByAccountAndPeriod(alice, start, end)).thenReturn(List.of());

        ReportsModel data = service.load(alice, ym);

        assertThat(data.byCategory()).hasSize(2);
        assertThat(data.byCategory().get(0).categoryId()).isEqualTo("moradia");
        assertThat(data.byCategory().get(0).name()).isEqualTo("Moradia");
        assertThat(data.byCategory().get(0).colorHex()).isEqualTo("#abcdef");
        assertThat(data.byCategory().get(0).amount().toPlainString()).isEqualTo("500.00");
        assertThat(data.byCategory().get(1).amount().toPlainString()).isEqualTo("80.00");
    }

    @Test
    void loadPorContaFiltraZeradasEOrdenaDesc() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        stubAggregator();

        UUID cartao = UUID.randomUUID();
        UUID boleto = UUID.randomUUID();
        UUID vazia = UUID.randomUUID();

        Account cAcc = account(cartao, "Cartão", "#ff0000");
        Account bAcc = account(boleto, "Aluguel", null);
        Account vAcc = account(vazia, "Sem uso", "#00ff00");
        when(accountRepo.findAllByUserIdOrderByNameAsc(alice))
                .thenReturn(List.of(cAcc, bAcc, vAcc));

        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.of());
        when(txRepo.aggregateByAccountAndPeriod(alice, start, end)).thenReturn(List.<Object[]>of(
                new Object[] { cartao, 12_000L, 5_000L, 3L },
                new Object[] { boleto, 90_000L, 90_000L, 1L },
                new Object[] { vazia, 0L, 0L, 0L }));

        ReportsModel data = service.load(alice, ym);

        // zeradas fora; ordenado desc: Aluguel(900) > Cartão(120)
        assertThat(data.byAccount()).hasSize(2);
        assertThat(data.byAccount().get(0).name()).isEqualTo("Aluguel");
        assertThat(data.byAccount().get(0).amount().toPlainString()).isEqualTo("900.00");
        // conta sem cor → fallback
        assertThat(data.byAccount().get(0).colorHex()).isEqualTo("#6366f1");
        assertThat(data.byAccount().get(1).name()).isEqualTo("Cartão");
        assertThat(data.byAccount().get(1).colorHex()).isEqualTo("#ff0000");
    }

    @Test
    void loadMonta6MesesDeTendenciaEMarcaMesCorrente() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        stubAggregator();

        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.of());
        when(txRepo.aggregateByAccountAndPeriod(alice, start, end)).thenReturn(List.of());

        ReportsModel data = service.load(alice, ym);

        assertThat(data.trailing6()).hasSize(6);
        assertThat(data.trailing6().get(0).yearMonth()).isEqualTo("2025-12");
        assertThat(data.trailing6().get(5).yearMonth()).isEqualTo("2026-05");
        assertThat(data.trailing6().get(5).current()).isTrue();
        assertThat(data.current().current()).isTrue();
        assertThat(data.periodStart()).isEqualTo(start);
        assertThat(data.periodEndExclusive()).isEqualTo(end);
    }

    // ── helpers ──────────────────────────────────────────────

    private static MonthSummaryDto summary(YearMonth ym, boolean current) {
        BigDecimal z = new BigDecimal("0.00");
        return new MonthSummaryDto(ym.toString(), ym.toString(),
                z, z, z, z, z, z, z, z, null, current);
    }

    private static Category category(String id, String pt, String en, String color) {
        Category c = new Category();
        c.setId(id);
        c.setNamePt(pt);
        c.setNameEn(en);
        c.setColorHex(color);
        return c;
    }

    private static Account account(UUID id, String name, String color) {
        Account a = new Account();
        a.setId(id);
        a.setName(name);
        a.setKind(AccountKind.CARD);
        a.setColorHex(color);
        return a;
    }
}
