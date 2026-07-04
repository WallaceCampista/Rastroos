package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

class UserRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository users;

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
}
