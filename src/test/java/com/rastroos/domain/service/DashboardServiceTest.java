package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Pageable;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.BalancePointDto;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.DashboardModel;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TransactionRepository txRepo;
    @Mock private IncomeRepository incomeRepo;
    @Mock private AccountRepository accountsRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private AccountService accountService;

    @InjectMocks private DashboardService service;

    private final UUID alice = UUID.randomUUID();

    @Test
    void loadCalculaKpisCorretosEAEspectroDeBalance() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);

        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(500_000L);
        when(txRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(300_000L);
        when(txRepo.sumPaidByUserAndPeriod(alice, start, end)).thenReturn(100_000L);
        when(txRepo.findUpcomingUnpaid(eq(alice), eq(start), any(Pageable.class)))
                .thenReturn(List.of());
        when(txRepo.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(alice, start, end))
                .thenReturn(List.of());
        when(incomeRepo.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(alice, start, end))
                .thenReturn(List.of());
        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.of());
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of());
        when(accountsRepo.countByUserId(alice)).thenReturn(0L);
        when(accountService.topAccountsForMonth(eq(alice), eq(ym), eq(6))).thenReturn(List.of());

        DashboardModel data = service.load(alice, ym);

        assertThat(data.kpis().received().toPlainString()).isEqualTo("5000.00");
        assertThat(data.kpis().spent().toPlainString()).isEqualTo("3000.00");
        assertThat(data.kpis().paid().toPlainString()).isEqualTo("1000.00");
        assertThat(data.kpis().toPay().toPlainString()).isEqualTo("2000.00");
        assertThat(data.kpis().balance().toPlainString()).isEqualTo("4000.00");
        assertThat(data.periodStart()).isEqualTo(start);
        assertThat(data.periodEndExclusive()).isEqualTo(end);
        // série de 31 dias (maio) mesmo vazia, todos zerados
        assertThat(data.balanceSeries()).hasSize(31);
        assertThat(data.balanceSeries())
                .allMatch(p -> p.balance().signum() == 0);
    }

    @Test
    void loadEnriqueceUpcomingComNomeDaConta() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        UUID accountId = UUID.randomUUID();
        Account a = new Account();
        a.setId(accountId);
        a.setUserId(alice);
        a.setName("Cartão da Alice");
        a.setKind(AccountKind.CARD);

        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setUserId(alice);
        t.setAccountId(accountId);
        t.setCategoryId("outros");
        t.setDescription("Mercado");
        t.setAmountCents(12_300L);
        t.setDueDate(LocalDate.of(2026, 5, 20));
        t.setFixed(false);

        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.sumPaidByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.findUpcomingUnpaid(eq(alice), eq(start), any(Pageable.class)))
                .thenReturn(List.of(t));
        when(txRepo.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(alice, start, end))
                .thenReturn(List.of());
        when(incomeRepo.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(alice, start, end))
                .thenReturn(List.of());
        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.of());
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(a));
        when(accountsRepo.countByUserId(alice)).thenReturn(1L);
        when(accountService.topAccountsForMonth(eq(alice), eq(ym), eq(6))).thenReturn(List.of());

        DashboardModel data = service.load(alice, ym);

        assertThat(data.upcoming()).hasSize(1);
        assertThat(data.upcoming().get(0).accountName()).isEqualTo("Cartão da Alice");
        assertThat(data.upcoming().get(0).amount().toPlainString()).isEqualTo("123.00");
    }

    @Test
    void loadCobreCasoQuandoPagoMaiorQueGasto() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(1_000L);
        when(txRepo.sumPaidByUserAndPeriod(alice, start, end)).thenReturn(1_500L);
        when(txRepo.findUpcomingUnpaid(eq(alice), eq(start), any(Pageable.class)))
                .thenReturn(List.of());
        when(txRepo.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(alice, start, end))
                .thenReturn(List.of());
        when(incomeRepo.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(alice, start, end))
                .thenReturn(List.of());
        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.of());
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of());
        when(accountsRepo.countByUserId(alice)).thenReturn(0L);
        when(accountService.topAccountsForMonth(eq(alice), eq(ym), eq(6))).thenReturn(List.of());

        DashboardModel data = service.load(alice, ym);

        assertThat(data.kpis().toPay().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void balanceSeriesAcumulaIncomeMenosPagoPorDia() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        Income salario = new Income();
        salario.setUserId(alice);
        salario.setSource("Salário");
        salario.setAmountCents(500_000L);
        salario.setIncomeDate(LocalDate.of(2026, 5, 5));

        Transaction aluguelPago = new Transaction();
        aluguelPago.setUserId(alice);
        aluguelPago.setAccountId(UUID.randomUUID());
        aluguelPago.setCategoryId("moradia");
        aluguelPago.setDescription("Aluguel");
        aluguelPago.setAmountCents(200_000L);
        aluguelPago.setDueDate(LocalDate.of(2026, 5, 10));
        aluguelPago.setPaid(true);

        // pendente — não deve mexer no saldo corrido
        Transaction cartaoPendente = new Transaction();
        cartaoPendente.setUserId(alice);
        cartaoPendente.setAccountId(UUID.randomUUID());
        cartaoPendente.setCategoryId("outros");
        cartaoPendente.setDescription("Cartão");
        cartaoPendente.setAmountCents(150_000L);
        cartaoPendente.setDueDate(LocalDate.of(2026, 5, 20));
        cartaoPendente.setPaid(false);

        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(500_000L);
        when(txRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(350_000L);
        when(txRepo.sumPaidByUserAndPeriod(alice, start, end)).thenReturn(200_000L);
        when(txRepo.findUpcomingUnpaid(eq(alice), eq(start), any(Pageable.class)))
                .thenReturn(List.of());
        when(txRepo.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(alice, start, end))
                .thenReturn(List.of(aluguelPago, cartaoPendente));
        when(incomeRepo.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(alice, start, end))
                .thenReturn(List.of(salario));
        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.of());
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of());
        when(accountsRepo.countByUserId(alice)).thenReturn(0L);
        when(accountService.topAccountsForMonth(eq(alice), eq(ym), eq(6))).thenReturn(List.of());

        DashboardModel data = service.load(alice, ym);

        List<BalancePointDto> series = data.balanceSeries();
        assertThat(series).hasSize(31);
        assertThat(series.get(3).balance().toPlainString()).isEqualTo("0.00");   // dia 4 -> 0
        assertThat(series.get(4).balance().toPlainString()).isEqualTo("5000.00"); // dia 5 -> salário
        assertThat(series.get(8).balance().toPlainString()).isEqualTo("5000.00"); // dia 9 -> ainda só salário
        assertThat(series.get(9).balance().toPlainString()).isEqualTo("3000.00"); // dia 10 -> aluguel pago
        assertThat(series.get(30).balance().toPlainString()).isEqualTo("3000.00");// fim do mês, pendente não conta
    }

    @Test
    void byCategoryEnriqueceComNomePtEColor() {
        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        Category moradia = new Category();
        moradia.setId("moradia");
        moradia.setNamePt("Moradia");
        moradia.setNameEn("Housing");
        moradia.setColorHex("#abcdef");

        Category outros = new Category();
        outros.setId("outros");
        outros.setNamePt("Outros");
        outros.setNameEn("Others");
        outros.setColorHex("#123456");

        when(incomeRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.sumAmountByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.sumPaidByUserAndPeriod(alice, start, end)).thenReturn(0L);
        when(txRepo.findUpcomingUnpaid(eq(alice), eq(start), any(Pageable.class)))
                .thenReturn(List.of());
        when(txRepo.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(alice, start, end))
                .thenReturn(List.of());
        when(incomeRepo.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(alice, start, end))
                .thenReturn(List.of());
        when(txRepo.aggregateByCategoryAndPeriod(alice, start, end)).thenReturn(List.<Object[]>of(
                new Object[] { "moradia", 200_000L },
                new Object[] { "outros",   50_000L }
        ));
        when(categoryRepo.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(moradia, outros));
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of());
        when(accountsRepo.countByUserId(alice)).thenReturn(0L);
        when(accountService.topAccountsForMonth(eq(alice), eq(ym), eq(6))).thenReturn(List.of());

        DashboardModel data = service.load(alice, ym);

        List<CategoryBreakdownDto> by = data.byCategory();
        assertThat(by).hasSize(2);
        assertThat(by.get(0).categoryId()).isEqualTo("moradia");
        assertThat(by.get(0).name()).isEqualTo("Moradia");
        assertThat(by.get(0).colorHex()).isEqualTo("#abcdef");
        assertThat(by.get(0).amount().toPlainString()).isEqualTo("2000.00");
        assertThat(by.get(1).amount().toPlainString()).isEqualTo("500.00");
    }
}
