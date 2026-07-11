package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre com Postgres real as duas queries novas da Etapa 11:
 * {@link TransactionRepository#aggregateTotalsByPeriod} (total/pago/fixo em
 * uma linha) e {@link InvestmentHistoryRepository#findAllByUserId} (snapshots
 * dos investimentos do usuário via subquery). Isolamento por usuário é central.
 */
class ReportAggregateRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository tx;
    @Autowired private InvestmentRepository investments;
    @Autowired private InvestmentHistoryRepository history;

    @Test
    void aggregateTotalsByPeriodSomaTotalPagoEFixoSomenteDoUsuario() {
        User alice = users.saveAndFlush(newUser("a-tot@example.com"));
        User bob = users.saveAndFlush(newUser("b-tot@example.com"));

        Account aliceCard = newAccount(alice, "Cartão", AccountKind.CARD);
        Account bobCard = newAccount(bob, "Cartão", AccountKind.CARD);

        // Alice, maio
        save(alice, aliceCard, "Aluguel", 10_000L, LocalDate.of(2026, 5, 10), true, true);
        save(alice, aliceCard, "Mercado", 5_000L, LocalDate.of(2026, 5, 3), false, false);
        // fora do período — não conta
        save(alice, aliceCard, "Abril", 9_999L, LocalDate.of(2026, 4, 30), true, true);
        // Bob — não pode vazar
        save(bob, bobCard, "Cinema", 100_000L, LocalDate.of(2026, 5, 4), true, true);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);

        List<Object[]> rows = tx.aggregateTotalsByPeriod(alice.getId(), start, end);
        assertThat(rows).hasSize(1);
        Object[] r = rows.get(0);

        assertThat(((Number) r[0]).longValue()).isEqualTo(15_000L); // total
        assertThat(((Number) r[1]).longValue()).isEqualTo(10_000L); // pago
        assertThat(((Number) r[2]).longValue()).isEqualTo(10_000L); // fixo
    }

    @Test
    void findAllByUserIdTrazSnapshotsDoUsuarioOrdenadosSemVazar() {
        User alice = users.saveAndFlush(newUser("a-hist@example.com"));
        User bob = users.saveAndFlush(newUser("b-hist@example.com"));

        Investment aliceCdb = newInvestment(alice, "CDB");
        Investment alicePiggy = newInvestment(alice, "Viagem");
        Investment bobCdb = newInvestment(bob, "CDB Bob");

        history.saveAndFlush(snapshot(aliceCdb, "2026-04", 100_000L));
        history.saveAndFlush(snapshot(aliceCdb, "2026-03", 90_000L));
        history.saveAndFlush(snapshot(alicePiggy, "2026-05", 20_000L));
        // Bob — não pode vazar
        history.saveAndFlush(snapshot(bobCdb, "2026-05", 999_000L));

        List<InvestmentHistory> all = history.findAllByUserId(alice.getId());

        assertThat(all).hasSize(3);
        assertThat(all).extracting(InvestmentHistory::getInvestmentId)
                .doesNotContain(bobCdb.getId());

        // dentro do mesmo investimento, ordem cronológica
        List<String> cdbMonths = all.stream()
                .filter(h -> h.getInvestmentId().equals(aliceCdb.getId()))
                .map(InvestmentHistory::getYearMonth)
                .toList();
        assertThat(cdbMonths).containsExactly("2026-03", "2026-04");
    }

    // ── helpers ──────────────────────────────────────────────

    private static User newUser(String email) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private Account newAccount(User u, String name, AccountKind kind) {
        Account a = new Account();
        a.setUserId(u.getId());
        a.setName(name);
        a.setKind(kind);
        a.setFixed(false);
        return accounts.saveAndFlush(a);
    }

    private void save(User user, Account account, String desc, long cents,
                      LocalDate due, boolean paid, boolean fixed) {
        Transaction t = new Transaction();
        t.setUserId(user.getId());
        t.setAccountId(account.getId());
        t.setCategoryId("outros");
        t.setDescription(desc);
        t.setAmountCents(cents);
        t.setDueDate(due);
        t.setPaid(paid);
        t.setFixed(fixed);
        tx.saveAndFlush(t);
    }

    private Investment newInvestment(User u, String name) {
        Investment i = new Investment();
        i.setUserId(u.getId());
        i.setName(name);
        i.setKind(InvestmentKind.CDI);
        i.setAmountCents(0L);
        return investments.saveAndFlush(i);
    }

    private static InvestmentHistory snapshot(Investment inv, String ym, long cents) {
        InvestmentHistory h = new InvestmentHistory();
        h.setInvestmentId(inv.getId());
        h.setYearMonth(ym);
        h.setAmountCents(cents);
        return h;
    }
}
