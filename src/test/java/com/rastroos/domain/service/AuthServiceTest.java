package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.entity.enums.VerificationPurpose;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.domain.repository.UserSessionRepository;
import com.rastroos.domain.service.AuthService.SignupOutcome;
import com.rastroos.security.PasswordPolicy;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private UserSessionRepository sessions;
    @Mock private PasswordEncoder encoder;
    @Mock private VerificationCodeService codes;
    @Mock private EmailService email;

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private AuthService service;

    private static final String STRONG = "Abcdef1!";

    private AuthService service() {
        if (service == null) {
            service = new AuthService(users, sessions, encoder, passwordPolicy, codes, email);
        }
        return service;
    }

    // ── signup ───────────────────────────────────────────────

    @Test
    void signupComSenhaFracaRetornaErrosSemPersistir() {
        SignupOutcome out = service().signup("Ana", "ana@example.com", "weak");

        assertThat(out.created()).isFalse();
        assertThat(out.passwordErrors()).isNotEmpty();
        verify(users, never()).save(any());
        verifyNoInteractions(email, codes);
    }

    @Test
    void signupComEmailExistenteFingeSucessoSemVazar() {
        when(users.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);

        SignupOutcome out = service().signup("Ana", "ana@example.com", STRONG);

        assertThat(out.created()).isFalse();
        assertThat(out.userId()).isNull();
        verify(users, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void signupFelizCriaPendingEEnviaCodigo() {
        when(users.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(encoder.encode(STRONG)).thenReturn("HASH");
        UUID id = UUID.randomUUID();
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(id);
            return u;
        });
        when(codes.issue(id, VerificationPurpose.EMAIL_VERIFY)).thenReturn("123456");

        SignupOutcome out = service().signup("  Ana  ", "  Ana@Example.com ", STRONG);

        assertThat(out.created()).isTrue();
        assertThat(out.userId()).isEqualTo(id);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.USER);
        verify(email).sendVerificationCode("ana@example.com", "123456");
    }

    // ── verifyEmail ──────────────────────────────────────────

    @Test
    void verifyEmailUsuarioInexistenteFalha() {
        when(users.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.empty());
        assertThat(service().verifyEmail("x@example.com", "123456")).isFalse();
    }

    @Test
    void verifyEmailJaVerificadoRetornaTrueSemChecarCodigo() {
        User u = user("a@example.com");
        u.setEmailVerified(true);
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(u));

        assertThat(service().verifyEmail("a@example.com", "000000")).isTrue();
        verifyNoInteractions(codes);
    }

    @Test
    void verifyEmailCodigoInvalidoFalha() {
        User u = user("a@example.com");
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(u));
        when(codes.verifyAndConsume(u.getId(), VerificationPurpose.EMAIL_VERIFY, "bad"))
                .thenReturn(false);

        assertThat(service().verifyEmail("a@example.com", "bad")).isFalse();
        assertThat(u.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmailFelizMarcaVerificado() {
        User u = user("a@example.com");
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(u));
        when(codes.verifyAndConsume(u.getId(), VerificationPurpose.EMAIL_VERIFY, "123456"))
                .thenReturn(true);

        assertThat(service().verifyEmail("a@example.com", "123456")).isTrue();
        assertThat(u.isEmailVerified()).isTrue();
        verify(users).save(u);
    }

    // ── requestPasswordReset ─────────────────────────────────

    @Test
    void requestPasswordResetPresenteEmiteEEnvia() {
        User u = user("a@example.com");
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(u));
        when(codes.issue(u.getId(), VerificationPurpose.PASSWORD_RESET)).thenReturn("654321");

        service().requestPasswordReset("a@example.com");

        verify(email).sendPasswordResetCode("a@example.com", "654321");
    }

    @Test
    void requestPasswordResetAusenteNaoFazNada() {
        when(users.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.empty());
        service().requestPasswordReset("x@example.com");
        verifyNoInteractions(email, codes);
    }

    // ── confirmPasswordReset ─────────────────────────────────

    @Test
    void confirmResetSenhaFracaRetornaErros() {
        List<String> errs = service().confirmPasswordReset("a@example.com", "123456", "weak");
        assertThat(errs).isNotEmpty();
        verifyNoInteractions(codes);
    }

    @Test
    void confirmResetUsuarioInexistenteRetornaInvalidCode() {
        when(users.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.empty());
        assertThat(service().confirmPasswordReset("x@example.com", "123456", STRONG))
                .containsExactly("reset.invalidCode");
    }

    @Test
    void confirmResetCodigoInvalidoRetornaInvalidCode() {
        User u = user("a@example.com");
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(u));
        when(codes.verifyAndConsume(u.getId(), VerificationPurpose.PASSWORD_RESET, "bad"))
                .thenReturn(false);

        assertThat(service().confirmPasswordReset("a@example.com", "bad", STRONG))
                .containsExactly("reset.invalidCode");
    }

    @Test
    void confirmResetFelizTrocaHashERevogaSessoes() {
        User u = user("a@example.com");
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(u));
        when(codes.verifyAndConsume(u.getId(), VerificationPurpose.PASSWORD_RESET, "123456"))
                .thenReturn(true);
        when(encoder.encode(STRONG)).thenReturn("NEWHASH");

        List<String> errs = service().confirmPasswordReset("a@example.com", "123456", STRONG);

        assertThat(errs).isEmpty();
        assertThat(u.getPasswordHash()).isEqualTo("NEWHASH");
        assertThat(u.isPasswordMustChange()).isFalse();
        verify(sessions).revokeAllByUser(eq(u.getId()), any(Instant.class));
    }

    // ── changePassword ───────────────────────────────────────

    @Test
    void changePasswordSenhaFracaRetornaErros() {
        assertThat(service().changePassword(UUID.randomUUID(), "old", "weak")).isNotEmpty();
        verifyNoInteractions(sessions);
    }

    @Test
    void changePasswordUsuarioInexistente() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThat(service().changePassword(id, "old", STRONG)).containsExactly("user.notFound");
    }

    @Test
    void changePasswordSenhaAtualErrada() {
        User u = user("a@example.com");
        when(users.findById(u.getId())).thenReturn(Optional.of(u));
        when(encoder.matches("wrong", u.getPasswordHash())).thenReturn(false);

        assertThat(service().changePassword(u.getId(), "wrong", STRONG))
                .containsExactly("password.currentWrong");
    }

    @Test
    void changePasswordFelizTrocaHashERevogaSessoes() {
        User u = user("a@example.com");
        when(users.findById(u.getId())).thenReturn(Optional.of(u));
        when(encoder.matches("old", u.getPasswordHash())).thenReturn(true);
        when(encoder.encode(STRONG)).thenReturn("NEWHASH");

        List<String> errs = service().changePassword(u.getId(), "old", STRONG);

        assertThat(errs).isEmpty();
        assertThat(u.getPasswordHash()).isEqualTo("NEWHASH");
        verify(sessions).revokeAllByUser(eq(u.getId()), any(Instant.class));
    }

    // ── helper ───────────────────────────────────────────────

    private static User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setName("Fulano");
        u.setEmail(email);
        u.setPasswordHash("$2a$12$oldhash");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }
}
