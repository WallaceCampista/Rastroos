package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.TransactionDto;
import com.rastroos.web.dto.TransactionFilter;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.dto.UpcomingTransactionDto;

/**
 * Monta o <strong>pacote de fatos</strong> do usuário: o dossiê que acompanha
 * toda pergunta feita ao Alfredo.
 *
 * <p>É aqui que mora a promessa de exatidão. Todo número que o Alfredo pode
 * citar sai <em>deste</em> texto, calculado pelos mesmos services que
 * alimentam as telas — sempre filtrados por {@code userId} (§2.2). O modelo
 * recebe ordem explícita de não produzir nenhum valor que não esteja aqui, e o
 * que falta ele deve dizer que não tem. Busca por similaridade não participa
 * de conta nenhuma: ela entra à parte, só para localizar texto livre.
 *
 * <p>O tamanho é limitado de propósito (tetos por seção): contexto é o que se
 * paga em toda pergunta, então cabe o que responde a maioria das dúvidas sem
 * transformar cada mensagem numa fatura.
 *
 * <p>Somente leitura: abre uma transação de leitura e não escreve nada.
 */
@Component
public class FinancialContextBuilder {

    /** Quantos meses de histórico agregado entram no dossiê. */
    static final int HISTORY_MONTHS = 12;

    private static final int MAX_CATEGORIES = 8;
    private static final int MAX_ACCOUNTS = 15;
    private static final int MAX_OPEN_ENTRIES = 20;
    private static final int MAX_INCOMES = 12;
    private static final int MAX_INVESTMENTS = 12;
    private static final int MAX_UPCOMING = 8;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final DashboardService dashboard;
    private final AccountService accounts;
    private final TransactionService transactions;
    private final IncomeService incomes;
    private final InvestmentService investments;
    private final MonthlyFinanceAggregator aggregator;
    private final Clock clock;

    public FinancialContextBuilder(DashboardService dashboard,
                                   AccountService accounts,
                                   TransactionService transactions,
                                   IncomeService incomes,
                                   InvestmentService investments,
                                   MonthlyFinanceAggregator aggregator,
                                   Clock clock) {
        this.dashboard = dashboard;
        this.accounts = accounts;
        this.transactions = transactions;
        this.incomes = incomes;
        this.investments = investments;
        this.aggregator = aggregator;
        this.clock = clock;
    }

    /** Dossiê completo do usuário para o mês de referência. */
    @Transactional(readOnly = true)
    public String build(UUID userId, YearMonth period) {
        LocalDate today = LocalDate.now(clock);
        StringBuilder sb = new StringBuilder(4096);

        sb.append("DADOS REAIS DE ").append(labelUpper(period))
          .append(" (conferidos no banco em ").append(today.format(DAY)).append(")\n");
        sb.append("Todo valor abaixo é exato. Não existe outro número disponível.\n");

        appendMonth(sb, userId, period);
        appendAccounts(sb, userId, period);
        appendOpenEntries(sb, userId, period);
        appendIncomes(sb, userId, period);
        appendInvestments(sb, userId);
        appendHistory(sb, userId, period);

        return sb.toString();
    }

    // ── Mês de referência ────────────────────────────────────────────────

    private void appendMonth(StringBuilder sb, UUID userId, YearMonth period) {
        DashboardModel data = dashboard.load(userId, period);
        DashboardKpisDto k = data.kpis();

        sb.append("\n## Resumo de ").append(InsightFactsBuilder.monthLabel(period)).append('\n');
        sb.append("- Total recebido: ").append(money(k.received())).append('\n');
        sb.append("- Total gasto: ").append(money(k.spent())).append('\n');
        sb.append("- Já pago: ").append(money(k.paid())).append('\n');
        sb.append("- Falta pagar: ").append(money(k.toPay())).append('\n');
        sb.append("- Saldo disponível (recebido - gasto): ").append(money(k.balance())).append('\n');
        sb.append("- Total investido: ").append(money(k.invested())).append('\n');
        sb.append("- Lançamentos no mês: ").append(data.entriesCount()).append('\n');

        if (!data.byCategory().isEmpty()) {
            sb.append("\n### Gastos por categoria\n");
            data.byCategory().stream().limit(MAX_CATEGORIES).forEach(c ->
                    sb.append("- ").append(c.name()).append(": ").append(money(c.amount())).append('\n'));
            appendOmitted(sb, data.byCategory().size(), MAX_CATEGORIES, "categorias");
        }

        if (!data.upcoming().isEmpty()) {
            sb.append("\n### Próximos vencimentos em aberto\n");
            data.upcoming().stream().limit(MAX_UPCOMING).forEach(u -> sb
                    .append("- ").append(u.dueDate().format(SHORT_DAY)).append(' ')
                    .append(u.description()).append(": ").append(money(u.amount()))
                    .append(" (conta: ").append(u.accountName()).append(")\n"));
        }
    }

    // ── Contas e cartões ─────────────────────────────────────────────────

    private void appendAccounts(StringBuilder sb, UUID userId, YearMonth period) {
        AccountsView view = accounts.listForMonth(userId, period);
        List<AccountSummaryDto> all = Stream.of(view.cards(), view.bills(), view.recurrent())
                .flatMap(List::stream)
                .toList();
        if (all.isEmpty()) {
            sb.append("\n## Contas e cartões\nNenhuma conta ou cartão cadastrado.\n");
            return;
        }

        sb.append("\n## Contas e cartões em ").append(InsightFactsBuilder.monthLabel(period)).append('\n');
        sb.append("- Total fixo do mês: ").append(money(view.totalFixed()))
          .append(" em ").append(view.fixedCount()).append(" contas\n");
        sb.append("- Já pago: ").append(money(view.paidFixed()))
          .append(" · Falta pagar: ").append(money(view.remainingFixed())).append('\n');

        all.stream().limit(MAX_ACCOUNTS).forEach(a -> {
            sb.append("- ").append(a.name());
            if (a.last4() != null && !a.last4().isBlank()) {
                sb.append(" (final ").append(a.last4()).append(')');
            }
            sb.append(": total ").append(money(a.total()))
              .append(", falta ").append(money(a.remaining()));
            if (a.dueDay() != null) {
                sb.append(", vence dia ").append(a.dueDay());
            }
            sb.append(", situação: ").append(statusLabel(a.status())).append('\n');
        });
        appendOmitted(sb, all.size(), MAX_ACCOUNTS, "contas");
    }

    // ── Lançamentos em aberto ────────────────────────────────────────────

    private void appendOpenEntries(StringBuilder sb, UUID userId, YearMonth period) {
        TransactionFilter unpaid = new TransactionFilter(
                TransactionFilter.PaidFilter.UNPAID, null, null,
                TransactionFilter.FixedFilter.ALL, null);
        TransactionsPageView page =
                transactions.listForMonth(userId, period, unpaid, 0, MAX_OPEN_ENTRIES);

        sb.append("\n## Lançamentos em aberto de ")
          .append(InsightFactsBuilder.monthLabel(period)).append('\n');
        if (page.items().isEmpty()) {
            sb.append("Nenhum: tudo o que foi lançado no mês já está pago.\n");
            return;
        }
        sb.append("São ").append(page.totalElements()).append(" em aberto.\n");
        for (TransactionDto t : page.items()) {
            sb.append("- ").append(t.dueDate().format(SHORT_DAY)).append(' ')
              .append(t.description()).append(": ").append(money(t.amount()))
              .append(" — ").append(t.accountName()).append(" / ").append(t.categoryName());
            if (t.installmentTotal() != null && t.installmentCurrent() != null) {
                sb.append(" (parcela ").append(t.installmentCurrent())
                  .append('/').append(t.installmentTotal()).append(')');
            }
            sb.append('\n');
        }
        appendOmitted(sb, (int) page.totalElements(), MAX_OPEN_ENTRIES, "lançamentos em aberto");
    }

    // ── Receitas ─────────────────────────────────────────────────────────

    private void appendIncomes(StringBuilder sb, UUID userId, YearMonth period) {
        IncomesPageView page =
                incomes.listForMonth(userId, period, IncomeFilter.empty(), 0, MAX_INCOMES);

        sb.append("\n## Receitas de ").append(InsightFactsBuilder.monthLabel(period)).append('\n');
        if (page.items().isEmpty()) {
            sb.append("Nenhuma receita registrada neste mês.\n");
            return;
        }
        sb.append("Recebido (confirmado) ").append(money(page.receivedAmount()))
          .append("; previsto no mês ").append(money(page.totalAmount()))
          .append(" em ").append(page.totalElements()).append(" receitas.\n");
        for (IncomeDto i : page.items()) {
            sb.append("- ").append(i.incomeDate().format(SHORT_DAY)).append(' ')
              .append(i.source()).append(": ").append(money(i.amount()));
            // Sem esta marca o modelo trata um recebimento programado para daqui
            // a três anos como dinheiro que já entrou.
            sb.append(i.received() ? " (recebido)" : " (a receber, ainda não confirmado)");
            if (i.categoryName() != null && !i.categoryName().isBlank()) {
                sb.append(" (").append(i.categoryName()).append(')');
            }
            sb.append('\n');
        }
        appendOmitted(sb, (int) page.totalElements(), MAX_INCOMES, "receitas");
    }

    // ── Investimentos ────────────────────────────────────────────────────

    private void appendInvestments(StringBuilder sb, UUID userId) {
        InvestmentsView view = investments.load(userId);
        sb.append("\n## Investimentos (posição atual, não depende do mês)\n");
        if (view.piggies().isEmpty() && view.portfolio().isEmpty()) {
            sb.append("Nenhum investimento cadastrado.\n");
            return;
        }
        sb.append("- Total investido: ").append(money(view.summary().totalInvested())).append('\n');
        sb.append("- Soma das metas: ").append(money(view.summary().totalGoals()));
        if (view.summary().piggyProgress() != null) {
            sb.append(" (").append(view.summary().piggyProgress()).append("% concluídas)");
        }
        sb.append('\n');
        sb.append("- Rendimento mensal estimado: ")
          .append(money(view.summary().monthlyReturn())).append('\n');

        List<InvestmentDto> all = Stream.concat(view.piggies().stream(), view.portfolio().stream())
                .toList();
        all.stream().limit(MAX_INVESTMENTS).forEach(i -> {
            sb.append("- ").append(i.name()).append(": ").append(money(i.amount()));
            if (i.goal() != null && i.goal().signum() > 0) {
                sb.append(" de uma meta de ").append(money(i.goal()));
                if (i.progressPercent() != null) {
                    sb.append(" (").append(i.progressPercent()).append("%)");
                }
            }
            if (i.rateLabel() != null && !i.rateLabel().isBlank()) {
                sb.append(", rendimento ").append(i.rateLabel());
            }
            sb.append('\n');
        });
        appendOmitted(sb, all.size(), MAX_INVESTMENTS, "aplicações");
    }

    // ── Histórico ────────────────────────────────────────────────────────

    private void appendHistory(StringBuilder sb, UUID userId, YearMonth period) {
        sb.append("\n## Histórico mês a mês (recebido · gasto · saldo · poupança)\n");
        List<YearMonth> axis = MonthlyFinanceAggregator.trailingAxis(period, HISTORY_MONTHS);
        boolean any = false;
        for (YearMonth ym : axis) {
            MonthSummaryDto m = aggregator.summarize(userId, ym, 0L, ym.equals(period));
            if (m.received().signum() == 0 && m.spent().signum() == 0) {
                continue; // mês sem movimento nenhum não ensina nada e ocupa contexto
            }
            any = true;
            sb.append("- ").append(InsightFactsBuilder.monthLabel(ym)).append(": ")
              .append(money(m.received())).append(" · ").append(money(m.spent()))
              .append(" · ").append(money(m.net()))
              .append(" · ")
              .append(m.savingsRate() == null ? "sem receita" : m.savingsRate() + "%")
              .append('\n');
        }
        if (!any) {
            sb.append("Sem movimento nos últimos ").append(HISTORY_MONTHS).append(" meses.\n");
        }
        sb.append("Fora desta janela de ").append(HISTORY_MONTHS)
          .append(" meses não há dado carregado — se perguntarem, diga que não tem.\n");
    }

    // ── Formatação ───────────────────────────────────────────────────────

    private static void appendOmitted(StringBuilder sb, int total, int limit, String noun) {
        if (total > limit) {
            sb.append("- (mais ").append(total - limit).append(' ').append(noun)
              .append(" não listadas aqui)\n");
        }
    }

    private static String money(BigDecimal value) {
        return InsightFactsBuilder.money(value);
    }

    private static String labelUpper(YearMonth ym) {
        return InsightFactsBuilder.monthLabel(ym).toUpperCase(java.util.Locale.forLanguageTag("pt-BR"));
    }

    /** Traduz o status técnico da conta para algo que o modelo entenda direto. */
    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "paid" -> "paga";
            case "overdue" -> "VENCIDA em aberto";
            case "soon" -> "vence em breve";
            case "open" -> "em aberto";
            default -> "sem lançamentos";
        };
    }
}
