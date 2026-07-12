package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.rastroos.domain.entity.LoginAttempt;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

class UserRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository users;

    @Autowired
    private LoginAttemptRepository attempts;

    @Test
    void adminSeededDoChangelog003DeveExistir() {
        assertThat(users.findByEmailIgnoreCase("admin@rastroos.local"))
                .isPresent()
                .get()
                .satisfies(u -> {
                    assertThat(u.getRole()).isEqualTo(UserRole.ADMIN);
                    assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    assertThat(u.isPasswordMustChange()).isTrue();
                    assertThat(u.getPasswordHash()).startsWith("$2a$12$");
                });
    }

    @Test
    void existsByEmailIgnoreCaseDeveSerCaseInsensitive() {
        assertThat(users.existsByEmailIgnoreCase("ADMIN@RASTROOS.LOCAL")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("noone@rastroos.local")).isFalse();
    }

    @Test
    void saveDeveAtribuirIdECreatedAtViaPrePersist() {
        User u = new User();
        u.setName("Maria Silva");
        u.setEmail("maria@example.com");
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.PENDING_APPROVAL);

        User saved = users.saveAndFlush(u);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getPreferredLocale()).isEqualTo("pt-BR");
    }

    @Test
    void searchComSentinelVazioNaoQuebraEFiltraPorStatusETexto() {
        users.saveAndFlush(newUser("Ana Search", "ana.search@example.com",
                UserRole.USER, UserStatus.ACTIVE));
        users.saveAndFlush(newUser("Bruno Search", "bruno.search@example.com",
                UserRole.USER, UserStatus.DISABLED));

        // Sem busca: o sentinela "" não quebra no Postgres (regressão CONCAT(NULL)).
        Page<User> all = users.search("", null, PageRequest.of(0, 50));
        assertThat(all.getTotalElements()).isGreaterThanOrEqualTo(3); // + admin do seed

        // Filtro por status.
        Page<User> disabled = users.search("", UserStatus.DISABLED, PageRequest.of(0, 50));
        assertThat(disabled.getContent()).extracting(User::getEmail)
                .contains("bruno.search@example.com")
                .doesNotContain("ana.search@example.com");

        // Filtro por texto (nome/email, case-insensitive).
        Page<User> byText = users.search("ANA.SEARCH", null, PageRequest.of(0, 50));
        assertThat(byText.getContent()).extracting(User::getEmail)
                .containsExactly("ana.search@example.com");
    }

    @Test
    void countByRoleEStatusRefletemOSeedAdmin() {
        assertThat(users.countByRole(UserRole.ADMIN)).isGreaterThanOrEqualTo(1L);
        assertThat(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE))
                .isGreaterThanOrEqualTo(1L);
    }

    @Test
    void loginHistoryVemMaisRecentePrimeiroECaseInsensitive() {
        attempts.save(attempt("hist@example.com", false, Instant.parse("2026-05-01T10:00:00Z")));
        attempts.save(attempt("hist@example.com", true, Instant.parse("2026-05-02T10:00:00Z")));
        attempts.flush();

        List<LoginAttempt> hist =
                attempts.findTop50ByEmailIgnoreCaseOrderByAttemptedAtDesc("HIST@EXAMPLE.COM");

        assertThat(hist).hasSize(2);
        assertThat(hist.get(0).isSuccess()).isTrue(); // o mais recente primeiro
    }

    private static User newUser(String name, String email, UserRole role, UserStatus status) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private static LoginAttempt attempt(String email, boolean success, Instant at) {
        LoginAttempt a = new LoginAttempt();
        a.setEmail(email);
        a.setSuccess(success);
        a.setAttemptedAt(at);
        return a;
    }
}
