package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.InsightFacts;
import com.rastroos.web.dto.InsightScreen;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.PortfolioSummaryDto;
import com.rastroos.web.dto.ReportsModel;
import com.rastroos.web.dto.TransactionFilterCounts;
import com.rastroos.web.dto.TransactionsPageView;

@ExtendWith(MockitoExtension.class)
class InsightFactsBuilderTest {

    @Mock private DashboardService dashboard;
    @Mock private AccountService accounts;
    @Mock private TransactionService transactions;
    @Mock private IncomeService incomes;
    @Mock private InvestmentService investments;
    @Mock private ReportService reports;
    @Mock private CompareService compare;

    @InjectMocks private InsightFactsBuilder builder;

    private final UUID userId = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 9);

    private static BigDecimal brl(String v) {
        return new BigDecimal(v);
    }

    // ── Visão geral ──────────────────────────────────────────────────────

    @Test
    void dashboard_comSaldoPositivoEContasEmAberto_resumeSituacaoECuidado() {
        when(dashboard.load(userId, period)).thenReturn(dashboardModel(
                new DashboardKpisDto(brl("8500.00"), brl("6200.00"), brl("4000.00"),
                        brl("2200.00"), brl("2300.00"), brl("900.00"))));

        InsightFacts facts = builder.build(userId, InsightScreen.DASHBOARD, period);

        assertThat(facts.periodLabel()).isEqualTo("setembro de 2026");
        assertThat(facts.lines()).contains(
                "Total recebido: R$ 8.500,00",
                "Total gasto: R$ 6.200,00",
                "Total investido: R$ 900,00",
                "Falta pagar: R$ 2.200,00",
                "Saldo disponível: R$ 2.300,00");
        assertThat(facts.fallbackText())
                .contains("R$ 8.500,00")
                .contains("Sobram R$ 2.300,00")
                .contains("Ainda faltam R$ 2.200,00");
    }

    @Test
    void dashboard_quandoFaltaPagarPassaOSaldo_alertaParaPriorizarVencimentos() {
        when(dashboard.load(userId, period)).thenReturn(dashboardModel(
                new DashboardKpisDto(brl("3000.00"), brl("2800.00"), brl("0.00"),
                        brl("2500.00"), brl("200.00"), BigDecimal.ZERO)));

        InsightFacts facts = builder.build(userId, InsightScreen.DASHBOARD, period);

        assertThat(facts.fallbackText()).contains("priorize os vencimentos");
    }

    @Test
    void dashboard_comSaldoNegativo_dizQueOsGastosPassaramDoQueEntrou() {
        when(dashboard.load(userId, period)).thenReturn(dashboardModel(
                new DashboardKpisDto(brl("2000.00"), brl("3500.00"), brl("0.00"),
                        brl("0.00"), brl("-1500.00"), BigDecimal.ZERO)));

        InsightFacts facts = builder.build(userId, InsightScreen.DASHBOARD, period);

        assertThat(facts.fallbackText())
                .contains("negativo em R$ 1.500,00")
                .doesNotContain("-R$ -");
    }

    @Test
    void dashboard_semLancamentos_pedeQueOUsuarioRegistre() {
        when(dashboard.load(userId, period)).thenReturn(dashboardModel(
                new DashboardKpisDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));

        InsightFacts facts = builder.build(userId, InsightScreen.DASHBOARD, period);

        assertThat(facts.fallbackText()).contains("Ainda não há lançamentos em setembro de 2026");
    }

    // ── Cartões & Contas ─────────────────────────────────────────────────

    @Test
    void cards_usaTotalFixoNumeroDeContasEFaltaPagar() {
        when(accounts.listForMonth(userId, period)).thenReturn(new AccountsView(
                List.of(account("Nubank", AccountKind.CARD, "1200.00", "0.00", "1200.00", "open")),
                List.of(account("Aluguel", AccountKind.BILL, "1800.00", "1800.00", "0.00", "paid")),
                List.of(),
                brl("3000.00"), brl("1800.00"), brl("1200.00"), 2));

        InsightFacts facts = builder.build(userId, InsightScreen.CARDS, period);

        assertThat(facts.lines()).contains(
                "Total fixo do mês: R$ 3.000,00",
                "Número de contas fixas: 2",
                "Falta pagar: R$ 1.200,00");
        assertThat(facts.fallbackText())
                .contains("R$ 3.000,00")
                .contains("2 contas")
                .contains("Falta pagar R$ 1.200,00")
                .contains("60% já quitado");
    }

    @Test
    void cards_comContaVencida_priorizaOAviso() {
        when(accounts.listForMonth(userId, period)).thenReturn(new AccountsView(
                List.of(account("Cartão", AccountKind.CARD, "900.00", "0.00", "900.00", "overdue")),
                List.of(), List.of(),
                brl("900.00"), brl("0.00"), brl("900.00"), 1));

        InsightFacts facts = builder.build(userId, InsightScreen.CARDS, period);

        assertThat(facts.fallbackText()).contains("1 conta está vencida");
    }

    // ── Gastos ───────────────────────────────────────────────────────────

    @Test
    void expenses_resumeTotalAbertoEPerfilDosLancamentos() {
        when(transactions.listForMonth(eq(userId), eq(period), any(), eq(0), eq(1)))
                .thenReturn(new TransactionsPageView(List.of(), 0, 1, 12, 12,
                        brl("2400.00"), brl("1000.00")));
        when(transactions.countsForMonth(eq(userId), eq(period), any()))
                .thenReturn(new TransactionFilterCounts(12, 5, 7, 3, 9));

        InsightFacts facts = builder.build(userId, InsightScreen.EXPENSES, period);

        assertThat(facts.lines()).contains(
                "Total lançado: R$ 2.400,00",
                "Em aberto: R$ 1.400,00");
        assertThat(facts.fallbackText())
                .contains("12 lançamentos")
                .contains("R$ 1.400,00 seguem em aberto")
                .contains("75% dos lançamentos");
    }

    // ── Receitas ─────────────────────────────────────────────────────────

    @Test
    void income_comFonteUnica_alertaSobreConcentracao() {
        when(incomes.listForMonth(eq(userId), eq(period), any(), eq(0), eq(1)))
                .thenReturn(new IncomesPageView(List.of(), 0, 1, 1, 1, brl("7000.00")));

        InsightFacts facts = builder.build(userId, InsightScreen.INCOME, period);

        assertThat(facts.lines()).contains("Total recebido: R$ 7.000,00", "Receitas registradas: 1");
        assertThat(facts.fallbackText()).contains("uma única fonte");
    }

    @Test
    void income_semReceitas_pedeORegistro() {
        when(incomes.listForMonth(eq(userId), eq(period), any(), eq(0), eq(1)))
                .thenReturn(new IncomesPageView(List.of(), 0, 1, 0, 0, BigDecimal.ZERO));

        InsightFacts facts = builder.build(userId, InsightScreen.INCOME, period);

        assertThat(facts.fallbackText()).contains("Nenhuma receita registrada");
    }

    // ── Investimentos ────────────────────────────────────────────────────

    @Test
    void investments_naoDependeDoMesEComentaProgressoDasMetas() {
        when(investments.load(userId)).thenReturn(new InvestmentsView(
                List.of(), List.of(),
                new PortfolioSummaryDto(brl("15000.00"), brl("30000.00"), 50,
                        brl("120.00"), Map.of()),
                null));

        InsightFacts facts = builder.build(userId, InsightScreen.INVESTMENTS, period);

        assertThat(facts.periodLabel()).isNull();
        assertThat(facts.fallbackText()).contains("ainda não tem investimentos cadastrados");
    }

    // ── Relatórios ───────────────────────────────────────────────────────

    @Test
    void reports_comparaOMesComAMediaDosAnteriores() {
        MonthSummaryDto current = month("2026-09", brl("5000.00"), brl("4000.00"), true);
        when(reports.load(userId, period)).thenReturn(new ReportsModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
                current,
                List.of(new CategoryBreakdownDto("food", "Alimentação", "#f00", brl("1600.00"))),
                List.of(),
                List.of(month("2026-08", brl("5000.00"), brl("2000.00"), false), current)));

        InsightFacts facts = builder.build(userId, InsightScreen.REPORTS, period);

        assertThat(facts.fallbackText())
                .contains("Alimentação lidera com R$ 1.600,00")
                .contains("40% do total")
                .contains("100% acima da média dos meses anteriores");
    }

    // ── Comparativo ──────────────────────────────────────────────────────

    @Test
    void compare_abaixoDaMeta_sugereSepararLogoAposReceber() {
        when(compare.load(userId, period)).thenReturn(new CompareModel(
                List.of(month("2026-09", brl("5000.00"), brl("4500.00"), true)),
                20, 10, 1, 6, "Mai", 18, List.of(), 0, 0));

        InsightFacts facts = builder.build(userId, InsightScreen.COMPARE, period);

        assertThat(facts.lines()).contains(
                "Meta de poupança: 20%",
                "Taxa média de poupança (6 meses): 10%",
                "Meses acima da meta: 1 de 6");
        assertThat(facts.fallbackText()).contains("separe o valor logo depois de receber");
    }

    @Test
    void compare_semMesesComReceita_pedeMaisHistorico() {
        when(compare.load(userId, period)).thenReturn(new CompareModel(
                List.of(), 20, null, 0, 0, null, null, List.of(), 0, 0));

        InsightFacts facts = builder.build(userId, InsightScreen.COMPARE, period);

        assertThat(facts.fallbackText()).contains("Ainda não há meses com receita para comparar");
    }

    // ── Formatação ───────────────────────────────────────────────────────

    @Test
    void moneyFormataEmBrlComSinalAntesDoSimbolo() {
        assertThat(InsightFactsBuilder.money(brl("1234.5"))).isEqualTo("R$ 1.234,50");
        assertThat(InsightFactsBuilder.money(brl("-90.00"))).isEqualTo("-R$ 90,00");
        assertThat(InsightFactsBuilder.money(null)).isEqualTo("R$ 0,00");
    }

    @Test
    void monthLabelUsaPortuguesIndependenteDoLocale() {
        assertThat(InsightFactsBuilder.monthLabel(YearMonth.of(2026, 3))).isEqualTo("março de 2026");
    }

    // ── fixtures ─────────────────────────────────────────────────────────


    // ── Ramos alternativos da narrativa ──────────────────────────────────

    @Test
    void dashboard_tudoPagoMasGastandoQuase100PorCentoDaReceita_avisaOComprometimento() {
        when(dashboard.load(userId, period)).thenReturn(dashboardModel(
                new DashboardKpisDto(brl("1000.00"), brl("950.00"), brl("950.00"),
                        brl("0.00"), brl("50.00"), BigDecimal.ZERO)));

        InsightFacts facts = builder.build(userId, InsightScreen.DASHBOARD, period);

        assertThat(facts.fallbackText())
                .contains("Tudo o que foi lançado no mês já está pago")
                .contains("95% da receita do mês");
    }

    @Test
    void dashboard_comReceitaMasSemGasto_pedeQueLanceOsGastos() {
        when(dashboard.load(userId, period)).thenReturn(dashboardModel(
                new DashboardKpisDto(brl("4000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, brl("4000.00"), BigDecimal.ZERO)));

        InsightFacts facts = builder.build(userId, InsightScreen.DASHBOARD, period);

        assertThat(facts.fallbackText()).contains("Nenhum gasto lançado ainda");
    }

    @Test
    void cards_semContasCadastradas_convidaACadastrarOsFixos() {
        when(accounts.listForMonth(userId, period)).thenReturn(new AccountsView(
                List.of(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));

        InsightFacts facts = builder.build(userId, InsightScreen.CARDS, period);

        assertThat(facts.fallbackText()).contains("ainda não cadastrou cartões nem contas");
    }

    @Test
    void cards_mesTodoQuitado_sugereRevisarAssinaturas() {
        when(accounts.listForMonth(userId, period)).thenReturn(new AccountsView(
                List.of(), List.of(account("Luz", AccountKind.BILL, "300.00", "300.00", "0.00", "paid")),
                List.of(),
                brl("300.00"), brl("300.00"), brl("0.00"), 1));

        InsightFacts facts = builder.build(userId, InsightScreen.CARDS, period);

        assertThat(facts.fallbackText())
                .contains("1 conta")
                .contains("Todos os fixos do mês já estão quitados")
                .contains("revisar assinaturas");
    }

    @Test
    void expenses_semLancamentos_pedeORegistro() {
        when(transactions.listForMonth(eq(userId), eq(period), any(), eq(0), eq(1)))
                .thenReturn(new TransactionsPageView(List.of(), 0, 1, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        when(transactions.countsForMonth(eq(userId), eq(period), any()))
                .thenReturn(TransactionFilterCounts.empty());

        InsightFacts facts = builder.build(userId, InsightScreen.EXPENSES, period);

        assertThat(facts.fallbackText()).contains("Nenhum gasto lançado em setembro de 2026");
    }

    @Test
    void expenses_tudoPagoEMaioriaFixa_apontaOsGastosQueSeRepetem() {
        when(transactions.listForMonth(eq(userId), eq(period), any(), eq(0), eq(1)))
                .thenReturn(new TransactionsPageView(List.of(), 0, 1, 10, 10,
                        brl("3000.00"), brl("3000.00")));
        when(transactions.countsForMonth(eq(userId), eq(period), any()))
                .thenReturn(new TransactionFilterCounts(10, 10, 0, 8, 2));

        InsightFacts facts = builder.build(userId, InsightScreen.EXPENSES, period);

        assertThat(facts.fallbackText())
                .contains("Está tudo pago.")
                .contains("maior parte é de gastos fixos");
    }

    @Test
    void income_comVariasFontes_lembraDeConferirSeTudoFoiLancado() {
        when(incomes.listForMonth(eq(userId), eq(period), any(), eq(0), eq(1)))
                .thenReturn(new IncomesPageView(List.of(), 0, 1, 3, 1, brl("9000.00")));

        InsightFacts facts = builder.build(userId, InsightScreen.INCOME, period);

        assertThat(facts.lines()).contains("Média por receita: R$ 3.000,00");
        assertThat(facts.fallbackText())
                .contains("3 receitas")
                .contains("mais de uma fonte");
    }

    @Test
    void investments_metasAdiantadas_pedeConstanciaNoAporte() {
        when(investments.load(userId)).thenReturn(new InvestmentsView(
                List.of(piggy("Casamento")), List.of(),
                new PortfolioSummaryDto(brl("15000.00"), brl("20000.00"), 75,
                        brl("120.00"), Map.of()),
                null));

        InsightFacts facts = builder.build(userId, InsightScreen.INVESTMENTS, period);

        assertThat(facts.fallbackText())
                .contains("R$ 15.000,00 investidos em 1 aplicação")
                .contains("R$ 120,00 por mês")
                .contains("75% concluídas")
                .contains("evite resgatar");
    }

    @Test
    void investments_metasAtrasadas_sugereAporteFixoAposReceber() {
        when(investments.load(userId)).thenReturn(new InvestmentsView(
                List.of(piggy("Reserva")), List.of(piggy("CDB")),
                new PortfolioSummaryDto(brl("2000.00"), brl("20000.00"), 10,
                        BigDecimal.ZERO, Map.of()),
                null));

        InsightFacts facts = builder.build(userId, InsightScreen.INVESTMENTS, period);

        assertThat(facts.fallbackText())
                .contains("2 aplicações")
                .contains("aporte fixo logo depois de receber")
                .doesNotContain("por mês");
    }

    @Test
    void investments_semMetas_pedeQueDefinaUmAlvo() {
        when(investments.load(userId)).thenReturn(new InvestmentsView(
                List.of(), List.of(piggy("Tesouro")),
                new PortfolioSummaryDto(brl("500.00"), BigDecimal.ZERO, null,
                        BigDecimal.ZERO, Map.of()),
                null));

        InsightFacts facts = builder.build(userId, InsightScreen.INVESTMENTS, period);

        assertThat(facts.fallbackText()).contains("Defina uma meta para cada cofrinho");
    }

    @Test
    void reports_mesVazio_dizQueFaltamDados() {
        when(reports.load(userId, period)).thenReturn(new ReportsModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
                month("2026-09", BigDecimal.ZERO, BigDecimal.ZERO, true),
                List.of(), List.of(), List.of()));

        InsightFacts facts = builder.build(userId, InsightScreen.REPORTS, period);

        assertThat(facts.fallbackText()).contains("Não há dados suficientes em setembro de 2026");
    }

    @Test
    void reports_gastoEmLinhaComAMedia_naoAlarmaAToa() {
        MonthSummaryDto current = month("2026-09", brl("5000.00"), brl("4000.00"), true);
        when(reports.load(userId, period)).thenReturn(new ReportsModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), current,
                List.of(), List.of(),
                List.of(month("2026-08", brl("5000.00"), brl("3900.00"), false), current)));

        InsightFacts facts = builder.build(userId, InsightScreen.REPORTS, period);

        assertThat(facts.fallbackText()).contains("em linha com a média dos meses anteriores");
    }

    @Test
    void reports_semHistoricoEComPoupancaBaixa_apontaOCorteMaisRapido() {
        MonthSummaryDto current = new MonthSummaryDto("2026-09", "Set",
                brl("5000.00"), brl("4800.00"), brl("4800.00"), BigDecimal.ZERO,
                brl("3000.00"), brl("1800.00"), brl("200.00"), BigDecimal.ZERO, 4, true);
        when(reports.load(userId, period)).thenReturn(new ReportsModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), current,
                List.of(), List.of(), List.of(current)));

        InsightFacts facts = builder.build(userId, InsightScreen.REPORTS, period);

        assertThat(facts.lines()).contains("Taxa de poupança: 4%");
        assertThat(facts.fallbackText()).contains("taxa de poupança está em 4%");
    }

    @Test
    void reports_semHistoricoEComPoupancaSaudavel_orientaPeloPesoDasCategorias() {
        MonthSummaryDto current = month("2026-09", brl("5000.00"), brl("3000.00"), true);
        when(reports.load(userId, period)).thenReturn(new ReportsModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), current,
                List.of(), List.of(), List.of(current)));

        InsightFacts facts = builder.build(userId, InsightScreen.REPORTS, period);

        assertThat(facts.fallbackText()).contains("peso de cada categoria");
    }

    @Test
    void compare_mesSelecionadoNegativo_mandaResolverIssoAntesDaMeta() {
        when(compare.load(userId, period)).thenReturn(new CompareModel(
                List.of(month("2026-09", brl("3000.00"), brl("4200.00"), true)),
                20, 5, 0, 4, "Mai", 9, List.of(), 0, 0));

        InsightFacts facts = builder.build(userId, InsightScreen.COMPARE, period);

        assertThat(facts.lines()).contains("Melhor mês: Mai (9%)");
        assertThat(facts.fallbackText()).contains("fechou negativo em R$ 1.200,00");
    }

    @Test
    void compare_acimaDaMeta_sugereSubirAMeta() {
        when(compare.load(userId, period)).thenReturn(new CompareModel(
                List.of(month("2026-09", brl("5000.00"), brl("3000.00"), true)),
                20, 32, 5, 6, "Jun", 40, List.of(), 0, 0));

        InsightFacts facts = builder.build(userId, InsightScreen.COMPARE, period);

        assertThat(facts.fallbackText())
                .contains("5 de 6 meses bateram a meta")
                .contains("acima da meta");
    }

    private static InvestmentDto piggy(String name) {
        return new InvestmentDto(UUID.randomUUID(), name, InvestmentKind.PIGGY,
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), 20,
                null, BigDecimal.ZERO, "#fff", "P");
    }

    private DashboardModel dashboardModel(DashboardKpisDto kpis) {
        return new DashboardModel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
                3, 12, kpis,
                List.of(), List.of(), List.of(),
                List.of(new CategoryBreakdownDto("food", "Alimentação", "#f00", brl("1200.00"))),
                List.of(), List.of());
    }

    private static AccountSummaryDto account(String name, AccountKind kind, String total,
                                             String paid, String remaining, String status) {
        return new AccountSummaryDto(UUID.randomUUID(), name, kind, "#fff", "C", null, null, null,
                new BigDecimal(total), new BigDecimal(paid), new BigDecimal(remaining),
                60, 3, status);
    }

    private static MonthSummaryDto month(String key, BigDecimal received, BigDecimal spent,
                                         boolean current) {
        BigDecimal fixed = spent.multiply(new BigDecimal("0.6"));
        return new MonthSummaryDto(key, key, received, spent, spent, BigDecimal.ZERO,
                fixed, spent.subtract(fixed), received.subtract(spent), BigDecimal.ZERO,
                20, current);
    }
}
