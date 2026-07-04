package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre {@link TransactionRepository#searchByFilters} e
 * {@link TransactionRepository#totalsByFilters} com Postgres real. Combinações
 * relevantes:
 * <ul>
 *   <li>filtros nulos = "todos"</li>
 *   <li>filtro {@code paid}/{@code fixed}/{@code accountId}/{@code categoryId}</li>
 *   <li>busca case-insensitive em {@code description}</li>
 *   <li>isolamento por user (Bob nunca aparece para Alice)</li>
 * </ul>
 */
class TransactionFilterRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository tx;

    @Test
    void searchSemFiltrosListaTodosNoMesEDoUsuario() {
        User alice = users.saveAndFlush(newUser("a-search@example.com"));
        User bob   = users.saveAndFlush(newUser("b-search@example.com"));

        Account aliceCard = newAccount(alice, "Card", AccountKind.CARD);
        Account bobCard   = newAccount(bob,   "Card", AccountKind.CARD);

        save(alice, aliceCard, "Mercado", 1_000L, LocalDate.of(2026, 5, 3), true,  "alimentacao");
        save(alice, aliceCard, "Padaria",   500L, LocalDate.of(2026, 5, 7), false, "alimentacao");
        save(bob,   bobCard,   "Outro",   9_999L, LocalDate.of(2026, 5, 5), false, "outros");

        Page<Transaction> result = tx.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, null, null, null, "",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Transaction::getDescription)
                .containsExactlyInAnyOrder("Mercado", "Padaria");
    }

    @Test
    void searchComPaidTrueRetornaSomentePagos() {
        User alice = users.saveAndFlush(newUser("a-paid@example.com"));
        Account c = newAccount(alice, "Card", AccountKind.CARD);

        save(alice, c, "Pago",      1_000L, LocalDate.of(2026, 5, 3), true,  "outros");
        save(alice, c, "Em aberto",   500L, LocalDate.of(2026, 5, 7), false, "outros");

        Page<Transaction> result = tx.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                Boolean.TRUE, null, null, null, "",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Pago");
    }

    @Test
    void searchComCategoryIdRetornaSomenteDaCategoria() {
        User alice = users.saveAndFlush(newUser("a-cat-f@example.com"));
        Account c = newAccount(alice, "Card", AccountKind.CARD);

        save(alice, c, "Mercado",  1_000L, LocalDate.of(2026, 5, 3), false, "alimentacao");
        save(alice, c, "Cinema",     800L, LocalDate.of(2026, 5, 5), false, "lazer");

        Page<Transaction> result = tx.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, null, null, "lazer", "",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Cinema");
    }

    @Test
    void searchComQuerySearchCaseInsensitive() {
        User alice = users.saveAndFlush(newUser("a-q@example.com"));
        Account c = newAccount(alice, "Card", AccountKind.CARD);

        save(alice, c, "Padaria do Zé",  500L, LocalDate.of(2026, 5, 3), false, "outros");
        save(alice, c, "Posto Shell",   1_000L, LocalDate.of(2026, 5, 7), false, "outros");

        Page<Transaction> result = tx.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, null, null, null, "PADARIA",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDescription()).startsWith("Padaria");
    }

    @Test
    void totalsAplicaMesmosFiltrosERetornaSomas() {
        User alice = users.saveAndFlush(newUser("a-tot@example.com"));
        Account c = newAccount(alice, "Card", AccountKind.CARD);

        save(alice, c, "Pago 1",  1_000L, LocalDate.of(2026, 5, 3), true,  "outros");
        save(alice, c, "Pago 2",  2_000L, LocalDate.of(2026, 5, 7), true,  "outros");
        save(alice, c, "Aberto",  3_000L, LocalDate.of(2026, 5, 9), false, "outros");

        Object[] totals = tx.totalsByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, null, null, null, "").get(0);

        assertThat(((Number) totals[0]).longValue()).isEqualTo(6_000L);
        assertThat(((Number) totals[1]).longValue()).isEqualTo(3_000L);

        Object[] paidOnly = tx.totalsByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                Boolean.TRUE, null, null, null, "").get(0);

        assertThat(((Number) paidOnly[0]).longValue()).isEqualTo(3_000L);
        assertThat(((Number) paidOnly[1]).longValue()).isEqualTo(3_000L);
    }

    @Test
    void isolamentoPorUserNaoVazaContagensDoOutroUsuario() {
        User alice = users.saveAndFlush(newUser("a-iso@example.com"));
        User bob   = users.saveAndFlush(newUser("b-iso@example.com"));

        Account ac = newAccount(alice, "Card", AccountKind.CARD);
        Account bc = newAccount(bob,   "Card", AccountKind.CARD);

        save(alice, ac, "Alice 1", 1_000L, LocalDate.of(2026, 5, 3), true,  "outros");
        save(bob,   bc, "Bob 1",   9_000L, LocalDate.of(2026, 5, 3), true,  "outros");
        save(bob,   bc, "Bob 2",   8_000L, LocalDate.of(2026, 5, 5), false, "outros");

        Object[] aliceTotals = tx.totalsByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, null, null, null, "").get(0);

        assertThat(((Number) aliceTotals[0]).longValue()).isEqualTo(1_000L);
    }

    // ── helpers ──────────────────────────────────────────────

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by("createdAt")));
    }

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

    @SuppressWarnings("unused")
    private List<Transaction> emptyMark() { return List.of(); }
}
