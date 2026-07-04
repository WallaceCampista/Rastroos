package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre as duas queries agregadas usadas pelo Dashboard / AccountService:
 * {@link TransactionRepository#aggregateByAccountAndPeriod} e
 * {@link TransactionRepository#aggregateByCategoryAndPeriod}, além do
 * {@code findUpcomingUnpaid} com paginação.
 *
 * <p>Por convenção do projeto, todo recurso de domínio é filtrado por
 * {@code user_id} — esse teste prova que dados do usuário B nunca vazam
 * na agregação do usuário A.
 */
class TransactionAggregateRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository tx;

    @Test
    void aggregateByAccountAndPeriodSomaTotalEPagoSomenteDoUsuario() {
        User alice = users.saveAndFlush(newUser("alice@example.com"));
        User bob   = users.saveAndFlush(newUser("bob@example.com"));

        Account aliceCard = newAccount(alice, "Cartão Alice", AccountKind.CARD);
        Account aliceBill = newAccount(alice, "Aluguel",       AccountKind.BILL);
        Account bobCard   = newAccount(bob,   "Cartão Bob",    AccountKind.CARD);

        // mês corrente
        save(alice, aliceCard, "Mercado",  3_490L, LocalDate.of(2026, 5, 3),  true);
        save(alice, aliceCard, "Padaria",  1_280L, LocalDate.of(2026, 5, 7),  false);
        save(alice, aliceBill, "Aluguel", 12_000L, LocalDate.of(2026, 5, 10), true);
        // do Bob: NÃO pode entrar na agregação da Alice
        save(bob,   bobCard,   "Cinema",   5_000L, LocalDate.of(2026, 5, 4), true);
        // fora do período
        save(alice, aliceCard, "Mês anterior", 9_999L, LocalDate.of(2026, 4, 30), true);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end   = LocalDate.of(2026, 6, 1);

        List<Object[]> rows = tx.aggregateByAccountAndPeriod(alice.getId(), start, end);
        Map<UUID, long[]> byAccount = rows.stream().collect(Collectors.toMap(
                r -> (UUID) r[0],
                r -> new long[] { ((Number) r[1]).longValue(),
                                  ((Number) r[2]).longValue(),
                                  ((Number) r[3]).longValue() }));

        assertThat(byAccount).hasSize(2);
        assertThat(byAccount).doesNotContainKey(bobCard.getId());

        long[] cardAgg = byAccount.get(aliceCard.getId());
        assertThat(cardAgg[0]).isEqualTo(4_770L);  // total
        assertThat(cardAgg[1]).isEqualTo(3_490L);  // pago
        assertThat(cardAgg[2]).isEqualTo(2L);      // count

        long[] billAgg = byAccount.get(aliceBill.getId());
        assertThat(billAgg[0]).isEqualTo(12_000L);
        assertThat(billAgg[1]).isEqualTo(12_000L);
        assertThat(billAgg[2]).isEqualTo(1L);
    }

    @Test
    void aggregateByCategoryAndPeriodOrdenaPorTotalDescECortaPorUser() {
        User alice = users.saveAndFlush(newUser("a-cat@example.com"));
        User bob   = users.saveAndFlush(newUser("b-cat@example.com"));

        Account aliceCard = newAccount(alice, "Card", AccountKind.CARD);
        Account bobCard   = newAccount(bob,   "Card", AccountKind.CARD);

        save(alice, aliceCard, "Mercado",  10_000L, LocalDate.of(2026, 5, 3),  true,  "alimentacao");
        save(alice, aliceCard, "Padaria",   2_000L, LocalDate.of(2026, 5, 7),  false, "alimentacao");
        save(alice, aliceCard, "Cinema",    8_000L, LocalDate.of(2026, 5, 9),  false, "lazer");
        save(alice, aliceCard, "Aluguel",  50_000L, LocalDate.of(2026, 5, 10), true,  "moradia");
        // Bob — não deve aparecer
        save(bob,   bobCard,   "Cinema",  100_000L, LocalDate.of(2026, 5, 4),  true,  "lazer");

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end   = LocalDate.of(2026, 6, 1);

        List<Object[]> rows = tx.aggregateByCategoryAndPeriod(alice.getId(), start, end);
        assertThat(rows).hasSize(3);

        // ordenado por total DESC: moradia(50k) > alimentacao(12k) > lazer(8k)
        assertThat((String) rows.get(0)[0]).isEqualTo("moradia");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(50_000L);

        assertThat((String) rows.get(1)[0]).isEqualTo("alimentacao");
        assertThat(((Number) rows.get(1)[1]).longValue()).isEqualTo(12_000L);

        assertThat((String) rows.get(2)[0]).isEqualTo("lazer");
        assertThat(((Number) rows.get(2)[1]).longValue()).isEqualTo(8_000L);
    }

    @Test
    void findUpcomingUnpaidIgnoraPagosEDePeriodoAnteriorERespeitaPageSize() {
        User alice = users.saveAndFlush(newUser("a-up@example.com"));
        Account card = newAccount(alice, "Cartão", AccountKind.CARD);

        // pago — não entra
        save(alice, card, "Já pago",      3_000L, LocalDate.of(2026, 5, 5),  true);
        // antes do "from" — não entra
        save(alice, card, "Mês passado",  4_000L, LocalDate.of(2026, 4, 10), false);
        // 6 unpaid no futuro
        save(alice, card, "Open 1", 1_000L, LocalDate.of(2026, 5, 12), false);
        save(alice, card, "Open 2", 1_000L, LocalDate.of(2026, 5, 14), false);
        save(alice, card, "Open 3", 1_000L, LocalDate.of(2026, 5, 18), false);
        save(alice, card, "Open 4", 1_000L, LocalDate.of(2026, 5, 21), false);
        save(alice, card, "Open 5", 1_000L, LocalDate.of(2026, 5, 25), false);
        save(alice, card, "Open 6", 1_000L, LocalDate.of(2026, 5, 29), false);

        List<Transaction> top5 = tx.findUpcomingUnpaid(
                alice.getId(), LocalDate.of(2026, 5, 1), PageRequest.of(0, 5));

        assertThat(top5).hasSize(5);
        // ordenado por dueDate asc
        assertThat(top5).extracting(Transaction::getDescription)
                .containsExactly("Open 1", "Open 2", "Open 3", "Open 4", "Open 5");
        assertThat(top5).extracting(Transaction::isPaid).containsOnly(false);
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
                      LocalDate due, boolean paid) {
        save(user, account, desc, cents, due, paid, "outros");
    }

    private void save(User user, Account account, String desc, long cents,
                      LocalDate due, boolean paid, String categoryId) {
        Transaction t = new Transaction();
        t.setUserId(user.getId());
        t.setAccountId(account.getId());
        t.setCategoryId(categoryId);
        t.setDescription(desc);
        t.setAmountCents(cents);
        t.setDueDate(due);
        t.setPaid(paid);
        tx.saveAndFlush(t);
    }
}
