package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.rastroos.domain.entity.LoginAttempt;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.UserSession;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.exception.BusinessRuleException;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.LoginAttemptRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.domain.repository.UserSessionRepository;
import com.rastroos.security.PasswordPolicy;
import com.rastroos.web.dto.UserAdminListView;
import com.rastroos.web.dto.UserDetailView;
import com.rastroos.web.form.UserCreateForm;
import com.rastroos.web.form.UserEditForm;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock private UserRepository users;
    @Mock private UserSessionRepository sessions;
    @Mock private LoginAttemptRepository loginAttempts;
    @Mock private PasswordEncoder encoder;

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private UserAdminService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private static final String STRONG = "Abcdef1!";

    @BeforeEach
    void setUp() {
        service = new UserAdminService(users, sessions, loginAttempts, encoder, passwordPolicy);
    }

    // ── create ───────────────────────────────────────────────

    @Test
    void createComSenhaForteSalvaComMustChangeEEmailVerificado() {
        when(encoder.encode(anyString())).thenReturn("HASH");
        when(users.existsByEmailIgnoreCase("nova@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserCreateForm form = new UserCreateForm();
        form.setName("  Maria  ");
        form.setEmail("  Nova@Example.com  ");
        form.setPassword(STRONG);
        form.setRole(UserRole.USER);
        form.setStatus(UserStatus.ACTIVE);

        UserAdminService.CreateResult result = service.create(form);

        assertThat(result.ok()).isTrue();
        assertThat(result.passwordErrors()).isEmpty();
        User u = result.user();
        assertThat(u.getName()).isEqualTo("Maria");
        assertThat(u.getEmail()).isEqualTo("nova@example.com");
        assertThat(u.getPasswordHash()).isEqualTo("HASH");
        assertThat(u.isPasswordMustChange()).isTrue();
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void createComSenhaFracaRetornaErrosSemSalvar() {
        UserCreateForm form = new UserCreateForm();
        form.setName("Maria");
        form.setEmail("nova@example.com");
        form.setPassword("weak");

        UserAdminService.CreateResult result = service.create(form);

        assertThat(result.ok()).isFalse();
        assertThat(result.passwordErrors()).isNotEmpty();
        verify(users, never()).save(any());
        verify(users, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    void createComEmailExistenteLancaBusinessRule() {
        when(users.existsByEmailIgnoreCase("nova@example.com")).thenReturn(true);

        UserCreateForm form = new UserCreateForm();
        form.setName("Maria");
        form.setEmail("nova@example.com");
        form.setPassword(STRONG);

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.emailTaken");
        verify(users, never()).save(any());
    }

    // ── update ───────────────────────────────────────────────

    @Test
    void updateEmailDuplicadoLancaBusinessRule() {
        User u = user(otherId, "old@example.com", UserRole.USER, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(users.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        UserEditForm form = editForm("Nome", "taken@example.com", UserRole.USER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.update(otherId, adminId, form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.emailTaken");
    }

    @Test
    void updateRebaixarUltimoAdminBloqueia() {
        User u = user(otherId, "admin2@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        UserEditForm form = editForm("Admin", "admin2@example.com", UserRole.USER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.update(otherId, adminId, form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.lastAdmin");
    }

    @Test
    void updateAutoRebaixamentoBloqueia() {
        User u = user(adminId, "me@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        when(users.findById(adminId)).thenReturn(Optional.of(u));

        UserEditForm form = editForm("Eu", "me@example.com", UserRole.USER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.update(adminId, adminId, form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.cannotDemoteSelf");
    }

    @Test
    void updateValidoAplicaEEmailNormalizado() {
        User u = user(otherId, "old@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserEditForm form = editForm("  Novo Nome  ", "New@Example.com", UserRole.USER, UserStatus.ACTIVE);

        User out = service.update(otherId, adminId, form);

        assertThat(out.getName()).isEqualTo("Novo Nome");
        assertThat(out.getEmail()).isEqualTo("new@example.com");
        assertThat(out.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ── changeStatus ─────────────────────────────────────────

    @Test
    void changeStatusAutoDesativacaoBloqueia() {
        assertThatThrownBy(() -> service.changeStatus(adminId, adminId, UserStatus.DISABLED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.cannotDisableSelf");
        verify(users, never()).findById(any());
    }

    @Test
    void changeStatusUltimoAdminBloqueia() {
        User u = user(otherId, "admin2@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeStatus(otherId, adminId, UserStatus.DISABLED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.lastAdmin");
    }

    @Test
    void changeStatusValidoAplica() {
        User u = user(otherId, "pending@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User out = service.changeStatus(otherId, adminId, UserStatus.ACTIVE);

        assertThat(out.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ── delete ───────────────────────────────────────────────

    @Test
    void deleteAutoExclusaoBloqueia() {
        assertThatThrownBy(() -> service.delete(adminId, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.cannotDeleteSelf");
        verify(users, never()).delete(any());
    }

    @Test
    void deleteUltimoAdminBloqueia() {
        User u = user(otherId, "admin2@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(otherId, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("users.lastAdmin");
        verify(users, never()).delete(any());
    }

    @Test
    void deleteUsuarioComumRemove() {
        User u = user(otherId, "user@example.com", UserRole.USER, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));

        service.delete(otherId, adminId);

        verify(users).delete(u);
    }

    // ── resetPassword ────────────────────────────────────────

    @Test
    void resetPasswordGeraSenhaForteMarcaTrocaERevogaSessoes() {
        User u = user(otherId, "user@example.com", UserRole.USER, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        when(encoder.encode(anyString())).thenReturn("HASH");

        String temp = service.resetPassword(otherId);

        assertThat(passwordPolicy.isStrong(temp)).isTrue();
        assertThat(u.getPasswordHash()).isEqualTo("HASH");
        assertThat(u.isPasswordMustChange()).isTrue();
        verify(sessions).revokeAllByUser(eq(otherId), any(Instant.class));
    }

    // ── detail / list ────────────────────────────────────────

    @Test
    void detailDeUsuarioInexistenteRetorna404() {
        when(users.findById(otherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(otherId, adminId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void detailMapeiaSessoesEHistoricoEMarcaSelf() {
        User u = user(adminId, "me@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        when(users.findById(adminId)).thenReturn(Optional.of(u));

        UserSession s = new UserSession();
        s.setId(UUID.randomUUID());
        s.setUserId(adminId);
        s.setUserAgent("Firefox");
        s.setIpAddress("192.0.2.1");
        s.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        s.setLastSeenAt(Instant.parse("2026-05-01T11:00:00Z"));
        when(sessions.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(adminId))
                .thenReturn(List.of(s));

        LoginAttempt a = new LoginAttempt();
        a.setEmail("me@example.com");
        a.setSuccess(true);
        a.setAttemptedAt(Instant.parse("2026-05-01T10:00:00Z"));
        when(loginAttempts.findTop50ByEmailIgnoreCaseOrderByAttemptedAtDesc("me@example.com"))
                .thenReturn(List.of(a));

        UserDetailView view = service.detail(adminId, adminId);

        assertThat(view.self()).isTrue();
        assertThat(view.sessions()).hasSize(1);
        assertThat(view.sessions().get(0).userAgent()).isEqualTo("Firefox");
        assertThat(view.loginHistory()).hasSize(1);
        assertThat(view.loginHistory().get(0).success()).isTrue();
    }

    @Test
    void listMontaKpisELinhas() {
        User u = user(otherId, "user@example.com", UserRole.USER, UserStatus.ACTIVE);
        Pageable pageable = PageRequest.of(0, 20);
        when(users.search(eq(""), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u), pageable, 1));
        when(sessions.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(otherId))
                .thenReturn(List.of());
        when(users.count()).thenReturn(5L);
        when(users.countByStatus(UserStatus.ACTIVE)).thenReturn(3L);
        when(users.countByStatus(UserStatus.PENDING_APPROVAL)).thenReturn(1L);
        when(users.countByStatus(UserStatus.DISABLED)).thenReturn(1L);
        when(users.countByRole(UserRole.ADMIN)).thenReturn(2L);

        UserAdminListView view = service.list(null, null, 0, 20);

        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).activeSessions()).isZero();
        assertThat(view.totalCount()).isEqualTo(5L);
        assertThat(view.activeCount()).isEqualTo(3L);
        assertThat(view.adminCount()).isEqualTo(2L);
    }

    @Test
    void terminateSessionDelegaAoRepositorio() {
        User u = user(otherId, "user@example.com", UserRole.USER, UserStatus.ACTIVE);
        when(users.findById(otherId)).thenReturn(Optional.of(u));
        UUID sessionId = UUID.randomUUID();
        when(sessions.revokeByIdAndUser(eq(sessionId), eq(otherId), any(Instant.class))).thenReturn(1);

        assertThat(service.terminateSession(otherId, sessionId)).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────

    private static User user(UUID id, String email, UserRole role, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setName("Fulano");
        u.setEmail(email);
        u.setRole(role);
        u.setStatus(status);
        u.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        u.setUpdatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        u.setPreferredLocale("pt-BR");
        return u;
    }

    private static UserEditForm editForm(String name, String email, UserRole role, UserStatus status) {
        UserEditForm f = new UserEditForm();
        f.setName(name);
        f.setEmail(email);
        f.setRole(role);
        f.setStatus(status);
        return f;
    }
}
