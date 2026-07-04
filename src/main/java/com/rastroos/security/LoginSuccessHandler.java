package com.rastroos.security;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.rastroos.domain.entity.LoginAttempt;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.UserSession;
import com.rastroos.domain.repository.LoginAttemptRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.domain.repository.UserSessionRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Pós-login bem-sucedido:
 * <ul>
 *   <li>atualiza {@code users.last_login_at}</li>
 *   <li>registra {@link LoginAttempt} com success=true</li>
 *   <li>cria uma {@link UserSession} (token hashado)</li>
 *   <li>audita o evento</li>
 *   <li>redireciona: se {@code passwordMustChange}, vai para /app/profile/change-password;
 *       senão, /app/dashboard</li>
 * </ul>
 */
@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final LoginAttemptRepository attempts;
    private final AuditLogger audit;

    public LoginSuccessHandler(UserRepository users,
                               UserSessionRepository sessions,
                               LoginAttemptRepository attempts,
                               AuditLogger audit) {
        this.users = users;
        this.sessions = sessions;
        this.attempts = attempts;
        this.audit = audit;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws ServletException, IOException {

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        Instant now = Instant.now();

        users.findById(principal.getId()).ifPresent((User u) -> {
            u.setLastLoginAt(now);
            users.save(u);
        });

        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmail(principal.getUsername());
        attempt.setIpAddress(clientIp(request));
        attempt.setSuccess(true);
        attempt.setAttemptedAt(now);
        attempts.save(attempt);

        UserSession session = new UserSession();
        session.setUserId(principal.getId());
        session.setTokenHash(hashSessionToken(request));
        session.setUserAgent(request.getHeader("User-Agent"));
        session.setIpAddress(clientIp(request));
        sessions.save(session);

        audit.record(principal.getId(), "LOGIN_SUCCESS", "users",
                     principal.getId().toString(), request, null);

        String target = principal.isPasswordMustChange()
                ? "/app/profile/change-password"
                : "/app/dashboard";
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private static String hashSessionToken(HttpServletRequest request) {
        try {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(random);
            String sid = request.getSession(false) != null
                    ? request.getSession().getId()
                    : "";
            md.update(sid.getBytes());
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }
}
