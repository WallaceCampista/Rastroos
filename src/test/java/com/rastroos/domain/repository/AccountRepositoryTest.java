package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Regra crítica: contas são privadas por usuário. Usuário A não pode
 * "ver" contas do usuário B — repositório enforça via {@code findByIdAndUserId}.
 */
class AccountRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository users;
    @Autowired
    private AccountRepository accounts;

    @Test
    void contaDeOutroUsuarioNuncaDeveSerEncontradaPeloFindByIdAndUserId() {
        User alice = users.saveAndFlush(newUser("alice@example.com"));
        User bob   = users.saveAndFlush(newUser("bob@example.com"));

        Account aliceCard = new Account();
        aliceCard.setUserId(alice.getId());
        aliceCard.setName("Cartão da Alice");
        aliceCard.setKind(AccountKind.CARD);
        aliceCard.setCloseDay((short) 5);
        aliceCard.setDueDay((short) 12);
        aliceCard.setFixed(false);
        aliceCard = accounts.saveAndFlush(aliceCard);

        assertThat(accounts.findByIdAndUserId(aliceCard.getId(), alice.getId())).isPresent();

        assertThat(accounts.findByIdAndUserId(aliceCard.getId(), bob.getId())).isEmpty();
    }

    @Test
    void findAllByUserIdRetornaApenasContasDoUsuario() {
        User alice = users.saveAndFlush(newUser("a1@example.com"));
        User bob   = users.saveAndFlush(newUser("b1@example.com"));

        accounts.saveAndFlush(newAccount(alice, "A1", AccountKind.BILL, true));
        accounts.saveAndFlush(newAccount(alice, "A2", AccountKind.RECURRENT, true));
        accounts.saveAndFlush(newAccount(bob,   "B1", AccountKind.CARD, false));

        List<Account> aliceList = accounts.findAllByUserIdOrderByNameAsc(alice.getId());
        List<Account> bobList   = accounts.findAllByUserIdOrderByNameAsc(bob.getId());

        assertThat(aliceList).hasSize(2).extracting(Account::getName).containsExactly("A1", "A2");
        assertThat(bobList).hasSize(1).extracting(Account::getName).containsExactly("B1");
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

    private static Account newAccount(User u, String name, AccountKind kind, boolean fixed) {
        Account a = new Account();
        a.setUserId(u.getId());
        a.setName(name);
        a.setKind(kind);
        a.setFixed(fixed);
        return a;
    }
}
