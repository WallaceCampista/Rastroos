package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.PortfolioSummaryDto;
import com.rastroos.web.dto.TransactionDto;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.dto.UpcomingTransactionDto;

/**
 * O dossiê de dados reais — a peça que sustenta a promessa de resposta
 * conferível. Se os números não chegarem aqui, o modelo os inventa; então o
 * teste verifica que cada bloco é montado e que tudo é lido do dono correto.
 */
@ExtendWith(MockitoExtension.class)
class FinancialContextBuilderTest {

    @Mock private DashboardService dashboard;
    @Mock private AccountService accounts;
    @Mock private TransactionService transactions;
    @Mock private IncomeService incomes;
    @Mock private InvestmentService investments;
    @Mock private MonthlyFinanceAggregator aggregator;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private final UUID alice = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 9);

    private FinancialContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new FinancialContextBuilder(dashboard, accounts, transactions, incomes,
                investments, aggregator, clock);
        stubAll();
    }

    @Test
    void dossieTrazOsKpisDoMesComValoresFormatados() {
        String context = builder.build(alice, period);

        assertThat(context)
                .contains("DADOS REAIS DE SETEMBRO DE 2026")
                .contains("Total recebido: R$ 8.500,00")
                .contains("Total gasto: R$ 6.200,00")
                .contains("Falta pagar: R$ 2.200,00")
                .contains("Saldo disponível (recebido - gasto): R$ 2.300,00");
    }

    @Test
    void dossieDizExplicitamenteQueNaoHaOutroNumeroDisponivel() {
        // É essa frase que autoriza o modelo a responder "não tenho esse dado"
        // em vez de preencher a lacuna com suposição.
        assertThat(builder.build(alice, period))
                .contains("Todo valor abaixo é exato. Não existe outro número disponível.");
    }

    @Test
    void dossieTrazCategorias_contasEmAberto_receitasEInvestimentos() {
        String context = builder.build(alice, period);

        assertThat(context)
                .contains("Alimentação: R$ 1.200,00")
                .contains("Nubank")
                .contains("final 1234")
                .contains("vence dia 10")
                .contains("Aluguel")
                .contains("Salário: R$ 8.000,00")
                .contains("Reserva de emergência")
                .contains("Total investido: R$ 12.000,00");
    }

    @Test
    void statusTecnicoDaContaEhTraduzido() {
        String context = builder.build(alice, period);

        assertThat(context).contains("VENCIDA em aberto");
        assertThat(context).doesNotContain("overdue");
    }

    @Test
    void historicoCobreDozeMesesEAvisaOLimiteDaJanela() {
        String context = builder.build(alice, period);

        verify(aggregator, org.mockito.Mockito.times(FinancialContextBuilder.HISTORY_MONTHS))
                .summarize(eq(alice), any(), anyLong(), anyBoolean());
        assertThat(context).contains("Fora desta janela de 12 meses não há dado carregado");
    }

    @Test
    void mesesSemMovimentoNaoOcupamContexto() {
        String context = builder.build(alice, period);

        // Só o mês corrente tem movimento no stub: os outros 11 saem fora.
        assertThat(context.lines().filter(l -> l.contains(" · R$ ")).count()).isEqualTo(1);
    }

    @Test
    void leSempreDoDonoDosDadosInformado() {
        builder.build(alice, period);

        verify(dashboard).load(alice, period);
        verify(accounts).listForMonth(alice, period);
        verify(investments).load(alice);
    }

    @Test
    void semNadaCadastrado_dizQueNaoHa_emVezDeOmitirASecao() {
        lenient().when(accounts.listForMonth(alice, period)).thenReturn(new AccountsView(
                List.of(), List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0));
        lenient().when(investments.load(alice)).thenReturn(new InvestmentsView(List.of(), List.of(),
                new PortfolioSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, null,
                        BigDecimal.ZERO, Map.of()), null));
        lenient().when(incomes.listForMonth(eq(alice), eq(period), any(), anyInt(), anyInt()))
                .thenReturn(new IncomesPageView(List.of(), 0, 10, 0, 0, BigDecimal.ZERO));

        String context = builder.build(alice, period);

        assertThat(context)
                .contains("Nenhuma conta ou cartão cadastrado.")
                .contains("Nenhum investimento cadastrado.")
                .contains("Nenhuma receita registrada neste mês.");
    }

    // ── stubs ────────────────────────────────────────────────────────────

    private void stubAll() {
        lenient().when(dashboard.load(alice, period)).thenReturn(new DashboardModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 3, 18,
                new DashboardKpisDto(brl("8500"), brl("6200"), brl("4000"), brl("2200"),
                        brl("2300"), brl("1000")),
                List.of(), List.of(), List.of(),
                List.of(new CategoryBreakdownDto("alimentacao", "Alimentação", "#fff", brl("1200"))),
                List.of(new UpcomingTransactionDto(UUID.randomUUID(), "Aluguel", UUID.randomUUID(),
                        "Casa", "moradia", brl("2000"), LocalDate.of(2026, 9, 15), true)),
                List.of()));

        lenient().when(accounts.listForMonth(alice, period)).thenReturn(new AccountsView(
                List.of(new AccountSummaryDto(UUID.randomUUID(), "Nubank", AccountKind.CARD,
                        "#820ad1", "N", "1234", (short) 3, (short) 10,
                        brl("1500"), brl("0"), brl("1500"), 0, 4, "overdue")),
                List.of(), List.of(), brl("3500"), brl("1300"), brl("2200"), 2));

        lenient().when(transactions.listForMonth(eq(alice), eq(period), any(), anyInt(), anyInt()))
                .thenReturn(new TransactionsPageView(List.of(
                        new TransactionDto(UUID.randomUUID(), "Aluguel", UUID.randomUUID(), "Casa",
                                "#fff", "moradia", "Moradia", "#fff", brl("2000"),
                                LocalDate.of(2026, 9, 15), true, false, null, null, null)),
                        0, 20, 1, 1, brl("2000"), brl("0")));

        lenient().when(incomes.listForMonth(eq(alice), eq(period), any(), anyInt(), anyInt()))
                .thenReturn(new IncomesPageView(List.of(
                        new IncomeDto(UUID.randomUUID(), "Salário", brl("8000"),
                                LocalDate.of(2026, 9, 5), "trabalho", "Trabalho", "#fff", null)),
                        0, 12, 1, 1, brl("8000")));

        lenient().when(investments.load(alice)).thenReturn(new InvestmentsView(
                List.of(new InvestmentDto(UUID.randomUUID(), "Reserva de emergência",
                        InvestmentKind.PIGGY, brl("12000"), brl("20000"), 60, null,
                        BigDecimal.ZERO, "#fff", "R")),
                List.of(),
                new PortfolioSummaryDto(brl("12000"), brl("20000"), 60, brl("90"), Map.of()),
                null));

        lenient().when(aggregator.summarize(eq(alice), any(), anyLong(), anyBoolean()))
                .thenAnswer(inv -> {
                    YearMonth ym = inv.getArgument(1);
                    boolean current = ym.equals(period);
                    return new MonthSummaryDto(ym.toString(), "Set",
                            current ? brl("8500") : BigDecimal.ZERO,
                            current ? brl("6200") : BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            current ? brl("2300") : BigDecimal.ZERO,
                            BigDecimal.ZERO, current ? 27 : null, current);
                });
    }

    private static BigDecimal brl(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
