package com.rastroos.domain.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.exception.BusinessRuleException;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.LoginAttemptRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.domain.repository.UserSessionRepository;
import com.rastroos.security.PasswordPolicy;
import com.rastroos.web.dto.LoginAttemptDto;
import com.rastroos.web.dto.UserAdminListView;
import com.rastroos.web.dto.UserDetailView;
import com.rastroos.web.dto.UserRowDto;
import com.rastroos.web.dto.UserSessionDto;
import com.rastroos.web.form.UserCreateForm;
import com.rastroos.web.form.UserEditForm;

/**
 * Regras de negócio da administração de usuários (admin-only).
 *
 * <p>Salvaguardas de "tiro no pé" (defesa em profundidade — as rotas já são
 * {@code hasRole('ADMIN')}):
 * <ul>
 *   <li>Um admin não pode se auto-desativar, se auto-excluir nem se rebaixar.</li>
 *   <li>Não é possível remover, desativar ou rebaixar o <em>último</em> admin
 *       ativo do sistema.</li>
 *   <li>Email é único (case-insensitive).</li>
 * </ul>
 * Nenhuma operação expõe hash de senha. O reset gera senha temporária forte,
 * marca {@code passwordMustChange} e revoga todas as sessões do alvo.
 */
@Service
public class UserAdminService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private static final int TEMP_PASSWORD_LENGTH = 14;
    private static final char[] LOWER = "abcdefghijkmnpqrstuvwxyz".toCharArray();
    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGIT = "23456789".toCharArray();
    private static final char[] SPECIAL = "!@#$%&*?".toCharArray();
    private static final char[] ALL = concat(LOWER, UPPER, DIGIT, SPECIAL);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final LoginAttemptRepository loginAttempts;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;

    public UserAdminService(UserRepository users,
                            UserSessionRepository sessions,
                            LoginAttemptRepository loginAttempts,
                            PasswordEncoder encoder,
                            PasswordPolicy passwordPolicy) {
        this.users = users;
        this.sessions = sessions;
        this.loginAttempts = loginAttempts;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
    }

    /** Resultado da criação: usuário criado ou lista de erros de política de senha. */
    public record CreateResult(User user, List<String> passwordErrors) {
        public boolean ok() {
            return user != null;
        }
    }

    @Transactional(readOnly = true)
    public UserAdminListView list(String statusStr, String search, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        UserStatus status = parseStatus(statusStr);
        String q = (search == null || search.isBlank()) ? "" : search.trim();

        Page<User> result = users.search(q, status, pageable);

        List<UserRowDto> items = result.getContent().stream()
                .map(u -> new UserRowDto(
                        u.getId(), u.getName(), u.getEmail(), u.isEmailVerified(),
                        u.getRole(), u.getStatus(), u.getCreatedAt(), u.getLastLoginAt(),
                        sessions.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(u.getId()).size()))
                .toList();

        return new UserAdminListView(
                items, safePage, safeSize,
                result.getTotalElements(), result.getTotalPages(),
                users.count(),
                users.countByStatus(UserStatus.ACTIVE),
                users.countByStatus(UserStatus.PENDING_APPROVAL),
                users.countByStatus(UserStatus.DISABLED),
                users.countByRole(UserRole.ADMIN));
    }

    @Transactional(readOnly = true)
    public UserDetailView detail(UUID id, UUID currentAdminId) {
        User u = require(id);

        List<UserSessionDto> activeSessions =
                sessions.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(id).stream()
                        .map(s -> new UserSessionDto(s.getId(), s.getUserAgent(),
                                s.getIpAddress(), s.getCreatedAt(), s.getLastSeenAt()))
                        .toList();

        List<LoginAttemptDto> history =
                loginAttempts.findTop50ByEmailIgnoreCaseOrderByAttemptedAtDesc(u.getEmail()).stream()
                        .map(a -> new LoginAttemptDto(a.getIpAddress(), a.isSuccess(), a.getAttemptedAt()))
                        .toList();

        return new UserDetailView(
                u.getId(), u.getName(), u.getEmail(), u.isEmailVerified(),
                u.getRole(), u.getStatus(), u.getPreferredLocale(),
                u.getCreatedAt(), u.getUpdatedAt(), u.getLastLoginAt(),
                u.isPasswordMustChange(), u.getId().equals(currentAdminId),
                activeSessions, history);
    }

    @Transactional
    public CreateResult create(UserCreateForm form) {
        List<String> pwdErrors = passwordPolicy.validate(form.getPassword());
        if (!pwdErrors.isEmpty()) {
            return new CreateResult(null, pwdErrors);
        }
        String email = form.getEmail().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("users.emailTaken");
        }

        User u = new User();
        u.setName(form.getName().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(form.getPassword()));
        u.setRole(form.getRole());
        u.setStatus(form.getStatus());
        u.setEmailVerified(true);          // conta criada pelo admin já é confiável
        u.setPasswordMustChange(true);     // obriga troca no primeiro login
        return new CreateResult(users.save(u), List.of());
    }

    @Transactional
    public User update(UUID id, UUID currentAdminId, UserEditForm form) {
        User u = require(id);
        String email = form.getEmail().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("users.emailTaken");
        }

        boolean losesAdminPower = form.getRole() != UserRole.ADMIN
                || form.getStatus() != UserStatus.ACTIVE;
        if (id.equals(currentAdminId) && losesAdminPower) {
            throw new BusinessRuleException("users.cannotDemoteSelf");
        }
        if (losesAdminPower && isLastActiveAdmin(u)) {
            throw new BusinessRuleException("users.lastAdmin");
        }

        u.setName(form.getName().trim());
        u.setEmail(email);
        u.setRole(form.getRole());
        u.setStatus(form.getStatus());
        return users.save(u);
    }

    @Transactional
    public User changeStatus(UUID id, UUID currentAdminId, UserStatus status) {
        if (id.equals(currentAdminId) && status != UserStatus.ACTIVE) {
            throw new BusinessRuleException("users.cannotDisableSelf");
        }
        User u = require(id);
        if (status != UserStatus.ACTIVE && isLastActiveAdmin(u)) {
            throw new BusinessRuleException("users.lastAdmin");
        }
        u.setStatus(status);
        return users.save(u);
    }

    @Transactional
    public void delete(UUID id, UUID currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BusinessRuleException("users.cannotDeleteSelf");
        }
        User u = require(id);
        if (isLastActiveAdmin(u)) {
            throw new BusinessRuleException("users.lastAdmin");
        }
        users.delete(u);
    }

    /** Gera senha temporária forte, força troca no próximo login e revoga sessões. */
    @Transactional
    public String resetPassword(UUID id) {
        User u = require(id);
        String temp = generateTempPassword();
        u.setPasswordHash(encoder.encode(temp));
        u.setPasswordMustChange(true);
        users.save(u);
        sessions.revokeAllByUser(id, Instant.now());
        return temp;
    }

    @Transactional
    public boolean terminateSession(UUID userId, UUID sessionId) {
        require(userId);
        return sessions.revokeByIdAndUser(sessionId, userId, Instant.now()) > 0;
    }

    @Transactional
    public int terminateAllSessions(UUID userId) {
        require(userId);
        return sessions.revokeAllByUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isEmailTaken(String email, UUID excludeId) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return users.findByEmailIgnoreCase(email.trim())
                .map(u -> !u.getId().equals(excludeId))
                .orElse(false);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private User require(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("users.notFound"));
    }

    private boolean isLastActiveAdmin(User u) {
        return u.getRole() == UserRole.ADMIN
                && u.getStatus() == UserStatus.ACTIVE
                && users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1;
    }

    private static UserStatus parseStatus(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String generateTempPassword() {
        char[] out = new char[TEMP_PASSWORD_LENGTH];
        out[0] = LOWER[RANDOM.nextInt(LOWER.length)];
        out[1] = UPPER[RANDOM.nextInt(UPPER.length)];
        out[2] = DIGIT[RANDOM.nextInt(DIGIT.length)];
        out[3] = SPECIAL[RANDOM.nextInt(SPECIAL.length)];
        for (int i = 4; i < out.length; i++) {
            out[i] = ALL[RANDOM.nextInt(ALL.length)];
        }
        // embaralha para não deixar as classes fixas nas 4 primeiras posições
        for (int i = out.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return new String(out);
    }

    private static char[] concat(char[]... groups) {
        int len = 0;
        for (char[] g : groups) {
            len += g.length;
        }
        char[] all = new char[len];
        int pos = 0;
        for (char[] g : groups) {
            System.arraycopy(g, 0, all, pos, g.length);
            pos += g.length;
        }
        return all;
    }
}
