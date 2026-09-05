package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.InsightFacts;
import com.rastroos.web.dto.InsightScreen;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.PortfolioSummaryDto;
import com.rastroos.web.dto.ReportsModel;
import com.rastroos.web.dto.TransactionFilter;
import com.rastroos.web.dto.TransactionFilterCounts;
import com.rastroos.web.dto.TransactionsPageView;

/**
 * Reúne os números que cada tela mostra e escreve, a partir deles, um resumo
 * determinístico (situação atual + cuidado a tomar).
 *
 * <p>Este resumo é a base de tudo: é o texto exibido quando não há IA
 * configurada (ou quando o provedor falha) e, quando há, é o material que a IA
 * reescreve — de forma que os valores citados sempre vêm daqui, calculados
 * pelos mesmos services que alimentam as telas. Nada é inventado.
 *
 * <p>Textos em PT-BR por decisão do domínio: a persona do Alfredo é
 * PT-BR (ver {@code AlfredoProperties#systemPrompt}).
 */
@Component
public class InsightFactsBuilder {

    /** Nomes dos meses fixos: o texto do Alfredo é PT-BR, sem depender do locale do request. */
    private static final String[] MONTHS = {
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    };

    /** Tolerância de arredondamento para "está tudo pago" (meio centavo). */
    private static final BigDecimal EPSILON = new BigDecimal("0.005");

    private final DashboardService dashboard;
    private final AccountService accounts;
    private final TransactionService transactions;
    private final IncomeService incomes;
    private final InvestmentService investments;
    private final ReportService reports;
    private final CompareService compare;

    public InsightFactsBuilder(DashboardService dashboard,
                               AccountService accounts,
                               TransactionService transactions,
                               IncomeService incomes,
                               InvestmentService investments,
                               ReportService reports,
                               CompareService compare) {
        this.dashboard = dashboard;
        this.accounts = accounts;
        this.transactions = transactions;
        this.incomes = incomes;
        this.investments = investments;
        this.reports = reports;
        this.compare = compare;
    }

    /**
     * Lê a tela pedida (pelos services de sempre, já isolados por
     * {@code userId}) e devolve os fatos + o resumo determinístico.
     */
    public InsightFacts build(UUID userId, InsightScreen screen, YearMonth period) {
        String periodLabel = screen.isPeriodic() ? monthLabel(period) : null;
        return switch (screen) {
            case DASHBOARD -> dashboardFacts(userId, period, periodLabel);
            case CARDS -> cardsFacts(userId, period, periodLabel);
            case EXPENSES -> expensesFacts(userId, period, periodLabel);
            case INCOME -> incomeFacts(userId, period, periodLabel);
            case INVESTMENTS -> investmentsFacts(userId);
            case REPORTS -> reportsFacts(userId, period, periodLabel);
            case COMPARE -> compareFacts(userId, period, periodLabel);
        };
    }

    // ── Visão geral ──────────────────────────────────────────────────────

    private InsightFacts dashboardFacts(UUID userId, YearMonth period, String periodLabel) {
        DashboardModel data = dashboard.load(userId, period);
        DashboardKpisDto k = data.kpis();

        List<String> lines = new ArrayList<>();
        lines.add(fact("Total recebido", k.received()));
        lines.add(fact("Total gasto", k.spent()));
        lines.add(fact("Total investido", k.invested()));
        lines.add(fact("Falta pagar", k.toPay()));
        lines.add(fact("Saldo disponível", k.balance()));
        lines.add("Lançamentos no mês: " + data.entriesCount());
        CategoryBreakdownDto top = data.byCategory().isEmpty() ? null : data.byCategory().get(0);
        if (top != null) {
            lines.add("Maior categoria de gasto: " + top.name() + " (" + money(top.amount()) + ")");
        }

        StringBuilder text = new StringBuilder();
        if (isZero(k.received()) && isZero(k.spent())) {
            text.append("Ainda não há lançamentos em ").append(periodLabel)
                .append(". Registre suas receitas e seus gastos para eu conseguir acompanhar o seu saldo.");
            return new InsightFacts(InsightScreen.DASHBOARD, periodLabel, lines, text.toString());
        }

        text.append("Em ").append(periodLabel).append(" você recebeu ").append(money(k.received()))
            .append(" e gastou ").append(money(k.spent()));
        if (k.invested().signum() > 0) {
            text.append(", com ").append(money(k.invested())).append(" investido");
        }
        text.append(". ");

        if (k.balance().signum() < 0) {
            text.append("O saldo está negativo em ").append(money(k.balance().abs()))
                .append(" — os gastos passaram do que entrou. ");
        } else {
            text.append("Sobram ").append(money(k.balance())).append(" de saldo disponível. ");
        }

        if (k.toPay().compareTo(EPSILON) > 0) {
            text.append("Ainda faltam ").append(money(k.toPay())).append(" a pagar");
            if (k.toPay().compareTo(k.balance()) > 0) {
                text.append(", mais do que o saldo disponível: priorize os vencimentos mais próximos"
                        + " e segure o que der para adiar.");
            } else {
                text.append(": deixe esse valor reservado antes de assumir qualquer gasto novo.");
            }
        } else if (k.spent().signum() > 0) {
            text.append("Tudo o que foi lançado no mês já está pago — bom momento para reforçar"
                    + " a reserva antes de novos compromissos.");
        } else {
            text.append("Nenhum gasto lançado ainda; lance-os para o saldo ficar fiel.");
        }

        Integer committed = percent(k.spent(), k.received());
        if (committed != null && committed >= 90 && k.balance().signum() >= 0) {
            text.append(" Atenção: os gastos já consumiram ").append(committed)
                .append("% da receita do mês.");
        }
        return new InsightFacts(InsightScreen.DASHBOARD, periodLabel, lines, text.toString());
    }

    // ── Cartões & Contas ─────────────────────────────────────────────────

    private InsightFacts cardsFacts(UUID userId, YearMonth period, String periodLabel) {
        AccountsView view = accounts.listForMonth(userId, period);

        List<AccountSummaryDto> all = Stream.of(view.cards(), view.bills(), view.recurrent())
                .flatMap(List::stream)
                .toList();
        long overdue = all.stream().filter(a -> "overdue".equals(a.status())).count();
        AccountSummaryDto biggest = all.stream()
                .filter(a -> a.remaining().signum() > 0)
                .max((a, b) -> a.remaining().compareTo(b.remaining()))
                .orElse(null);

        List<String> lines = new ArrayList<>();
        lines.add(fact("Total fixo do mês", view.totalFixed()));
        lines.add("Número de contas fixas: " + view.fixedCount());
        lines.add(fact("Já pago", view.paidFixed()));
        lines.add(fact("Falta pagar", view.remainingFixed()));
        lines.add("Cartões: " + view.cards().size()
                + " · Contas: " + view.bills().size()
                + " · Recorrentes: " + view.recurrent().size());
        lines.add("Contas vencidas em aberto: " + overdue);
        if (biggest != null) {
            lines.add("Maior valor em aberto: " + biggest.name() + " (" + money(biggest.remaining()) + ")");
        }

        StringBuilder text = new StringBuilder();
        if (all.isEmpty()) {
            text.append("Você ainda não cadastrou cartões nem contas. Cadastre os fixos do mês"
                    + " (aluguel, cartão, assinaturas) para eu prever seus vencimentos.");
            return new InsightFacts(InsightScreen.CARDS, periodLabel, lines, text.toString());
        }

        text.append("Seus fixos de ").append(periodLabel).append(" somam ").append(money(view.totalFixed()))
            .append(" em ").append(view.fixedCount())
            .append(view.fixedCount() == 1 ? " conta" : " contas").append(". ");

        if (view.remainingFixed().compareTo(EPSILON) > 0) {
            text.append("Falta pagar ").append(money(view.remainingFixed()));
            Integer paidPct = percent(view.paidFixed(), view.totalFixed());
            if (paidPct != null) {
                text.append(" (").append(paidPct).append("% já quitado)");
            }
            text.append(". ");
        } else {
            text.append("Todos os fixos do mês já estão quitados. ");
        }

        if (overdue > 0) {
            text.append("Atenção: ").append(overdue)
                .append(overdue == 1 ? " conta está vencida" : " contas estão vencidas")
                .append(" — resolva primeiro para não acumular juros e multa.");
        } else if (biggest != null) {
            text.append("O maior valor em aberto é ").append(biggest.name()).append(", com ")
                .append(money(biggest.remaining()))
                .append("; garanta esse valor em caixa antes do vencimento.");
        } else {
            text.append("Aproveite o mês em dia para revisar assinaturas que você não usa mais.");
        }
        return new InsightFacts(InsightScreen.CARDS, periodLabel, lines, text.toString());
    }

    // ── Gastos variáveis ─────────────────────────────────────────────────

    private InsightFacts expensesFacts(UUID userId, YearMonth period, String periodLabel) {
        TransactionsPageView page =
                transactions.listForMonth(userId, period, TransactionFilter.empty(), 0, 1);
        TransactionFilterCounts counts =
                transactions.countsForMonth(userId, period, TransactionFilter.empty());

        List<String> lines = new ArrayList<>();
        lines.add(fact("Total lançado", page.totalAmount()));
        lines.add(fact("Já pago", page.totalPaid()));
        lines.add(fact("Em aberto", page.totalUnpaid()));
        lines.add("Lançamentos no mês: " + counts.total()
                + " (em aberto: " + counts.unpaid() + ", pagos: " + counts.paid() + ")");
        lines.add("Fixos: " + counts.fixed() + " · Pontuais: " + counts.oneOff());

        StringBuilder text = new StringBuilder();
        if (counts.total() == 0) {
            text.append("Nenhum gasto lançado em ").append(periodLabel)
                .append(". Lance os gastos assim que acontecerem — é o que mantém o saldo confiável.");
            return new InsightFacts(InsightScreen.EXPENSES, periodLabel, lines, text.toString());
        }

        text.append("Em ").append(periodLabel).append(" são ").append(counts.total())
            .append(counts.total() == 1 ? " lançamento" : " lançamentos").append(", somando ")
            .append(money(page.totalAmount())).append(". ");

        if (page.totalUnpaid().compareTo(EPSILON) > 0) {
            text.append(money(page.totalUnpaid())).append(" seguem em aberto em ")
                .append(counts.unpaid())
                .append(counts.unpaid() == 1 ? " lançamento" : " lançamentos").append(". ");
        } else {
            text.append("Está tudo pago. ");
        }

        Integer oneOffPct = counts.total() == 0 ? null
                : (int) Math.round(counts.oneOff() * 100.0 / counts.total());
        if (oneOffPct != null && oneOffPct >= 60) {
            text.append("Os gastos pontuais são ").append(oneOffPct)
                .append("% dos lançamentos: vale revisar os do fim do mês, que costumam ser os evitáveis.");
        } else {
            text.append("A maior parte é de gastos fixos — o corte, quando precisar, sai mais fácil"
                    + " revisando o que se repete todo mês.");
        }
        return new InsightFacts(InsightScreen.EXPENSES, periodLabel, lines, text.toString());
    }

    // ── Receitas ─────────────────────────────────────────────────────────

    private InsightFacts incomeFacts(UUID userId, YearMonth period, String periodLabel) {
        IncomesPageView page = incomes.listForMonth(userId, period, IncomeFilter.empty(), 0, 1);
        long count = page.totalElements();

        List<String> lines = new ArrayList<>();
        lines.add(fact("Total recebido", page.totalAmount()));
        lines.add("Receitas registradas: " + count);
        if (count > 0) {
            lines.add(fact("Média por receita",
                    page.totalAmount().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)));
        }

        StringBuilder text = new StringBuilder();
        if (count == 0) {
            text.append("Nenhuma receita registrada em ").append(periodLabel)
                .append(". Sem elas o saldo e a taxa de poupança ficam distorcidos — registre salário,"
                        + " freelas e reembolsos.");
            return new InsightFacts(InsightScreen.INCOME, periodLabel, lines, text.toString());
        }

        text.append("Em ").append(periodLabel).append(" entraram ").append(money(page.totalAmount()))
            .append(" em ").append(count)
            .append(count == 1 ? " receita" : " receitas").append(". ");

        if (count == 1) {
            text.append("Tudo vem de uma única fonte: uma falha nela derruba o mês inteiro,"
                    + " então mantenha uma reserva equivalente a alguns meses de despesa.");
        } else {
            text.append("Ter mais de uma fonte ajuda a segurar imprevistos. ");
            text.append("Confira se todas as entradas do mês já estão lançadas antes de olhar o saldo.");
        }
        return new InsightFacts(InsightScreen.INCOME, periodLabel, lines, text.toString());
    }

    // ── Investimentos ────────────────────────────────────────────────────

    private InsightFacts investmentsFacts(UUID userId) {
        InvestmentsView view = investments.load(userId);
        PortfolioSummaryDto s = view.summary();

        List<String> lines = new ArrayList<>();
        lines.add(fact("Total investido", s.totalInvested()));
        lines.add(fact("Metas dos cofrinhos", s.totalGoals()));
        lines.add("Progresso das metas: "
                + (s.piggyProgress() == null ? "sem metas cadastradas" : s.piggyProgress() + "%"));
        lines.add(fact("Rendimento mensal estimado", s.monthlyReturn()));
        lines.add("Cofrinhos: " + view.piggies().size() + " · Carteira: " + view.portfolio().size());

        StringBuilder text = new StringBuilder();
        if (view.piggies().isEmpty() && view.portfolio().isEmpty()) {
            text.append("Você ainda não tem investimentos cadastrados. Comece por uma reserva de"
                    + " emergência: um cofrinho com meta já é suficiente para dar o primeiro passo.");
            return new InsightFacts(InsightScreen.INVESTMENTS, null, lines, text.toString());
        }

        text.append("Você tem ").append(money(s.totalInvested())).append(" investidos em ")
            .append(view.piggies().size() + view.portfolio().size())
            .append(view.piggies().size() + view.portfolio().size() == 1 ? " aplicação" : " aplicações")
            .append(". ");

        if (s.monthlyReturn().signum() > 0) {
            text.append("O rendimento estimado é de ").append(money(s.monthlyReturn())).append(" por mês. ");
        }

        if (s.piggyProgress() != null) {
            text.append("Suas metas estão ").append(s.piggyProgress()).append("% concluídas");
            if (s.piggyProgress() < 50) {
                text.append(" — um aporte fixo logo depois de receber costuma acelerar mais"
                        + " do que tentar guardar o que sobra.");
            } else {
                text.append("; mantenha o aporte constante e evite resgatar para cobrir gastos do mês.");
            }
        } else {
            text.append("Defina uma meta para cada cofrinho: sem alvo fica difícil medir progresso"
                    + " e o dinheiro acaba sendo resgatado no primeiro aperto.");
        }
        return new InsightFacts(InsightScreen.INVESTMENTS, null, lines, text.toString());
    }

    // ── Relatórios ───────────────────────────────────────────────────────

    private InsightFacts reportsFacts(UUID userId, YearMonth period, String periodLabel) {
        ReportsModel model = reports.load(userId, period);
        MonthSummaryDto c = model.current();

        List<String> lines = new ArrayList<>();
        lines.add(fact("Total recebido", c.received()));
        lines.add(fact("Total gasto", c.spent()));
        lines.add(fact("Gastos fixos", c.fixed()));
        lines.add(fact("Gastos pontuais", c.oneTime()));
        lines.add(fact("Falta pagar", c.toPay()));
        lines.add("Taxa de poupança: "
                + (c.savingsRate() == null ? "sem receita no mês" : c.savingsRate() + "%"));
        CategoryBreakdownDto top = model.byCategory().isEmpty() ? null : model.byCategory().get(0);
        if (top != null) {
            lines.add("Maior categoria: " + top.name() + " (" + money(top.amount()) + ")");
        }

        BigDecimal avgPrevious = averageSpentBefore(model.trailing6(), c.yearMonth());
        if (avgPrevious != null) {
            lines.add(fact("Média de gastos dos meses anteriores", avgPrevious));
        }

        StringBuilder text = new StringBuilder();
        if (isZero(c.received()) && isZero(c.spent())) {
            text.append("Não há dados suficientes em ").append(periodLabel)
                .append(" para montar o relatório. Assim que houver receitas e gastos lançados,"
                        + " os gráficos ganham sentido.");
            return new InsightFacts(InsightScreen.REPORTS, periodLabel, lines, text.toString());
        }

        text.append("Em ").append(periodLabel).append(", ").append(money(c.fixed()))
            .append(" dos gastos são fixos e ").append(money(c.oneTime())).append(" pontuais. ");

        if (top != null && c.spent().signum() > 0) {
            Integer share = percent(top.amount(), c.spent());
            text.append(top.name()).append(" lidera com ").append(money(top.amount()));
            if (share != null) {
                text.append(" (").append(share).append("% do total)");
            }
            text.append(". ");
        }

        if (avgPrevious != null && avgPrevious.signum() > 0) {
            Integer variation = percent(c.spent().subtract(avgPrevious).abs(), avgPrevious);
            boolean higher = c.spent().compareTo(avgPrevious) > 0;
            if (variation != null && variation >= 10) {
                text.append("O mês está ").append(variation).append("% ")
                    .append(higher ? "acima" : "abaixo").append(" da média dos meses anteriores")
                    .append(higher ? " — vale conferir o que saiu do padrão." : ", bom sinal.");
            } else {
                text.append("O gasto está em linha com a média dos meses anteriores.");
            }
        } else if (c.savingsRate() != null && c.savingsRate() < 10) {
            text.append("A taxa de poupança está em ").append(c.savingsRate())
                .append("%: cortar na categoria que lidera é o caminho mais rápido para subir.");
        } else {
            text.append("Use o peso de cada categoria para decidir onde cortar antes de apertar o resto.");
        }
        return new InsightFacts(InsightScreen.REPORTS, periodLabel, lines, text.toString());
    }

    /** Média de gastos dos meses da série que vêm antes do mês selecionado. */
    private static BigDecimal averageSpentBefore(List<MonthSummaryDto> trailing, String currentKey) {
        List<MonthSummaryDto> previous = trailing.stream()
                .filter(m -> m.yearMonth().compareTo(currentKey) < 0)
                .filter(m -> m.spent().signum() > 0)
                .toList();
        if (previous.isEmpty()) {
            return null;
        }
        BigDecimal sum = previous.stream()
                .map(MonthSummaryDto::spent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(previous.size()), 2, RoundingMode.HALF_UP);
    }

    // ── Comparativo ──────────────────────────────────────────────────────

    private InsightFacts compareFacts(UUID userId, YearMonth period, String periodLabel) {
        CompareModel model = compare.load(userId, period);
        MonthSummaryDto current = model.months().stream()
                .filter(MonthSummaryDto::current)
                .findFirst()
                .orElse(null);

        List<String> lines = new ArrayList<>();
        lines.add("Meta de poupança: " + model.target() + "%");
        lines.add("Taxa média de poupança (6 meses): "
                + (model.avgSavingsRate() == null ? "sem base de cálculo" : model.avgSavingsRate() + "%"));
        lines.add("Meses acima da meta: " + model.monthsAboveTarget() + " de " + model.monthsCounted());
        if (model.bestMonthLabel() != null && model.bestMonthRate() != null) {
            lines.add("Melhor mês: " + model.bestMonthLabel() + " (" + model.bestMonthRate() + "%)");
        }
        if (current != null) {
            lines.add(fact("Receita do mês selecionado", current.received()));
            lines.add(fact("Gasto do mês selecionado", current.spent()));
            lines.add(fact("Saldo do mês selecionado", current.net()));
        }

        StringBuilder text = new StringBuilder();
        if (model.monthsCounted() == 0) {
            text.append("Ainda não há meses com receita para comparar. Registre receitas e gastos"
                    + " por alguns meses e a evolução aparece aqui.");
            return new InsightFacts(InsightScreen.COMPARE, periodLabel, lines, text.toString());
        }

        text.append("Sua taxa média de poupança nos últimos meses é de ")
            .append(model.avgSavingsRate() == null ? 0 : model.avgSavingsRate())
            .append("%, contra uma meta de ").append(model.target()).append("%. ");
        text.append(model.monthsAboveTarget()).append(" de ").append(model.monthsCounted())
            .append(model.monthsCounted() == 1 ? " mês bateu a meta" : " meses bateram a meta").append(". ");

        boolean belowTarget = model.avgSavingsRate() == null || model.avgSavingsRate() < model.target();
        if (current != null && current.net().signum() < 0) {
            text.append(periodLabel).append(" fechou negativo em ").append(money(current.net().abs()))
                .append(": comece por aqui antes de pensar na meta.");
        } else if (belowTarget) {
            text.append("Para encostar na meta, separe o valor logo depois de receber"
                    + " — guardar o que sobra quase nunca funciona.");
        } else {
            text.append("O ritmo está acima da meta; considere subir a meta ou direcionar"
                    + " a diferença para um objetivo de longo prazo.");
        }
        return new InsightFacts(InsightScreen.COMPARE, periodLabel, lines, text.toString());
    }

    // ── Formatação ───────────────────────────────────────────────────────

    /** Rótulo do mês em PT-BR, ex.: {@code "setembro de 2026"}. */
    public static String monthLabel(YearMonth ym) {
        return MONTHS[ym.getMonthValue() - 1] + " de " + ym.getYear();
    }

    private static String fact(String label, BigDecimal value) {
        return label + ": " + money(value);
    }

    /**
     * Formata em BRL sem depender do locale do request (o texto do Alfredo é
     * sempre PT-BR) e com o sinal antes do símbolo: {@code -R$ 10,00}.
     */
    public static String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        DecimalFormat format = new DecimalFormat("#,##0.00",
                new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR")));
        String sign = safe.signum() < 0 ? "-" : "";
        return sign + "R$ " + format.format(safe.abs());
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.signum() == 0;
    }

    /** {@code part} sobre {@code whole} em %, ou {@code null} se não dá para dividir. */
    private static Integer percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() <= 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
