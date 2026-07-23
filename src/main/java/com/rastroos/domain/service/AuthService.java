package com.rastroos.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.entity.enums.VerificationPurpose;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.domain.repository.UserSessionRepository;
import com.rastroos.security.PasswordPolicy;

/**
 * Coordena os fluxos de autenticação que não pertencem ao Spring Security:
 * signup, verificação de email, troca de senha, reset de senha.
 *
 * <p>Regras de negócio críticas:
 * <ul>
 *   <li>Signup nunca falha "diferente" para email já existente — sempre
 *       responde "verifique seu email" (anti-enumeration).</li>
 *   <li>Após signup, status = PENDING_APPROVAL: o admin precisa liberar.</li>
 *   <li>Trocar senha invalida todas as outras sessões do usuário.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final VerificationCodeService codes;
    private final EmailService email;

    public AuthService(UserRepository users,
                       UserSessionRepository sessions,
                       PasswordEncoder encoder,
                       PasswordPolicy passwordPolicy,
                       VerificationCodeService codes,
                       EmailService email) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.codes = codes;
        this.email = email;
    }

    public record SignupOutcome(boolean created, UUID userId, List<String> passwordErrors) {
        public static SignupOutcome silentlyIgnored() {
            return new SignupOutcome(false, null, List.of());
        }
    }

    /**
     * Cria usuário em PENDING_APPROVAL + envia código de verificação de email.
     * Se email já cadastrado, finge sucesso (não vaza existência).
     */
    @Transactional
    public SignupOutcome signup(String name, String email, String password) {
        List<String> pwdErrors = passwordPolicy.validate(password);
        if (!pwdErrors.isEmpty()) return new SignupOutcome(false, null, pwdErrors);

        if (users.existsByEmailIgnoreCase(email)) {
            return SignupOutcome.silentlyIgnored();
        }

        User u = new User();
        u.setName(name == null ? "" : name.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.PENDING_APPROVAL);
        u = users.save(u);

        String code = codes.issue(u.getId(), VerificationPurpose.EMAIL_VERIFY);
        this.email.sendVerificationCode(u.getEmail(), code);

        return new SignupOutcome(true, u.getId(), List.of());
    }

    /** Marca email verificado se código bate. */
    @Transactional
    public boolean verifyEmail(String email, String code) {
        Optional<User> opt = users.findByEmailIgnoreCase(email);
        if (opt.isEmpty()) return false;
        User u = opt.get();
        if (u.isEmailVerified()) return true;
        if (!codes.verifyAndConsume(u.getId(), VerificationPurpose.EMAIL_VERIFY, code)) return false;
        u.setEmailVerified(true);
        users.save(u);
        return true;
    }

    /**
     * Inicia reset de senha: emite código se usuário existe; sempre retorna
     * void para não vazar existência.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            String code = codes.issue(u.getId(), VerificationPurpose.PASSWORD_RESET);
            this.email.sendPasswordResetCode(u.getEmail(), code);
        });
    }

    /** Conclui reset: valida código + política da nova senha. */
    @Transactional
    public List<String> confirmPasswordReset(String email, String code, String newPassword) {
        List<String> errs = passwordPolicy.validate(newPassword);
        if (!errs.isEmpty()) return errs;
        Optional<User> opt = users.findByEmailIgnoreCase(email);
        if (opt.isEmpty()) return List.of("reset.invalidCode");
        User u = opt.get();
        if (!codes.verifyAndConsume(u.getId(), VerificationPurpose.PASSWORD_RESET, code)) {
            return List.of("reset.invalidCode");
        }
        u.setPasswordHash(encoder.encode(newPassword));
        u.setPasswordMustChange(false);
        users.save(u);
        sessions.revokeAllByUser(u.getId(), java.time.Instant.now());
        return List.of();
    }

    /**
     * Troca de senha autenticada — exige senha atual.
     * Invalida todas as outras sessões do usuário.
     */
    @Transactional
    public List<String> changePassword(UUID userId, String currentPassword, String newPassword) {
        List<String> errs = passwordPolicy.validate(newPassword);
        if (!errs.isEmpty()) return errs;
        Optional<User> opt = users.findById(userId);
        if (opt.isEmpty()) return List.of("user.notFound");
        User u = opt.get();
        if (!encoder.matches(currentPassword, u.getPasswordHash())) {
            return List.of("password.currentWrong");
        }
        u.setPasswordHash(encoder.encode(newPassword));
        u.setPasswordMustChange(false);
        users.save(u);
        sessions.revokeAllByUser(userId, java.time.Instant.now());
        return List.of();
    }

    /** Atualiza o nome de exibição do próprio usuário. */
    @Transactional
    public void updateName(UUID userId, String name) {
        User u = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado: " + userId));
        u.setName(name == null ? "" : name.trim());
        users.save(u);
    }
}
