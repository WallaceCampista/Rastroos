package com.rastroos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Núcleo do isolamento do acessor: {@link CurrentUser#effectiveUserId()} aponta
 * para o titular quando a conta é ACESSOR, e para si mesmo caso contrário —
 * derivado sempre do principal autenticado.
 */
class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usuarioComum_idEfetivoEProprioIdEPodeExcluir() {
        User u = user(UserRole.USER, null, false);
        authenticate(u);

        assertThat(currentUser.requireEffectiveId()).isEqualTo(u.getId());
        assertThat(currentUser.requireId()).isEqualTo(u.getId());
        assertThat(currentUser.isAccessor()).isFalse();
        assertThat(currentUser.canDelete()).isTrue();
        assertThat(currentUser.isMaskActive()).isFalse();
    }

    @Test
    void acessor_idEfetivoEDoAlvoNaoPodeExcluirEMascaraAtiva() {
        UUID target = UUID.randomUUID();
        User u = user(UserRole.ACESSOR, target, true);
        authenticate(u);

        assertThat(currentUser.requireEffectiveId()).isEqualTo(target);   // dados = alvo
        assertThat(currentUser.requireId()).isEqualTo(u.getId());         // conta = própria
        assertThat(currentUser.isAccessor()).isTrue();
        assertThat(currentUser.canDelete()).isFalse();
        assertThat(currentUser.isMaskActive()).isTrue();
    }

    @Test
    void acessorSemMascara_naoAtivaMascara() {
        User u = user(UserRole.ACESSOR, UUID.randomUUID(), false);
        authenticate(u);

        assertThat(currentUser.isAccessor()).isTrue();
        assertThat(currentUser.isMaskActive()).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────

    private void authenticate(User u) {
        CustomUserDetails principal = new CustomUserDetails(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    private static User user(UserRole role, UUID accessesUserId, boolean masked) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("conta@example.com");
        u.setPasswordHash("HASH");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setAccessesUserId(accessesUserId);
        u.setValuesMasked(masked);
        return u;
    }
}
