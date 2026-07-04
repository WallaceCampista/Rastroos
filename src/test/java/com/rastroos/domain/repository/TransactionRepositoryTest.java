package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

class TransactionRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository tx;

    @Test
    void somaTotalEPagoBatemComOQueFoiInseridoNoMes() {
        User alice = users.saveAndFlush(newUser("alice2@example.com"));
        UUID acct = newAccount(alice).getId();

        save(alice.getId(), acct, "Mercado",         3_490L, LocalDate.of(2026, 5, 3),  true);
        save(alice.getId(), acct, "Padaria",         1_280L, LocalDate.of(2026, 5, 7),  false);
        save(alice.getId(), acct, "Cinema",          5_700L, LocalDate.of(2026, 5, 15), false);
        save(alice.getId(), acct, "Mês anterior",    9_999L, LocalDate.of(2026, 4, 30), true);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end   = LocalDate.of(2026, 6, 1);

        assertThat(tx.sumAmountByUserAndPeriod(alice.getId(), start, end)).isEqualTo(10_470L);
        assertThat(tx.sumPaidByUserAndPeriod(alice.getId(), start, end)).isEqualTo(3_490L);
        assertThat(tx.countByUserIdAndPaidFalse(alice.getId())).isEqualTo(2);
    }

    @Test
    void valorNegativoOuZeroEhBloqueadoPeloCheckConstraint() {
        User alice = users.saveAndFlush(newUser("alice3@example.com"));
        UUID acct = newAccount(alice).getId();

        Transaction bad = new Transaction();
        bad.setUserId(alice.getId());
        bad.setAccountId(acct);
        bad.setCategoryId("outros");
        bad.setDescription("Inválido");
        bad.setAmountCents(0L);
        bad.setDueDate(LocalDate.now());

        assertThatThrownBy(() -> tx.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void parcelaInconsistenteEhBloqueadaPeloCheckConstraint() {
        User alice = users.saveAndFlush(newUser("alice4@example.com"));
        UUID acct = newAccount(alice).getId();

        Transaction bad = new Transaction();
        bad.setUserId(alice.getId());
        bad.setAccountId(acct);
        bad.setCategoryId("outros");
        bad.setDescription("Parcela bug: current=5 total=3");
        bad.setAmountCents(1_000L);
        bad.setDueDate(LocalDate.now());
        bad.setInstallmentCurrent((short) 5);
        bad.setInstallmentTotal((short) 3);

        assertThatThrownBy(() -> tx.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private Account newAccount(User u) {
        Account a = new Account();
        a.setUserId(u.getId());
        a.setName("Cartão " + u.getEmail());
        a.setKind(AccountKind.CARD);
        a.setFixed(false);
        return accounts.saveAndFlush(a);
    }

    private void save(UUID userId, UUID accountId, String desc, long cents,
                      LocalDate due, boolean paid) {
        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setAccountId(accountId);
        t.setCategoryId("outros");
        t.setDescription(desc);
        t.setAmountCents(cents);
        t.setDueDate(due);
        t.setPaid(paid);
        tx.saveAndFlush(t);
    }
}
