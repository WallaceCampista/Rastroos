package com.rastroos.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.rastroos.domain.entity.LoginAttempt;
import com.rastroos.domain.repository.LoginAttemptRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Pós-login falho:
 * <ul>
 *   <li>registra {@link LoginAttempt} com success=false (alimenta o lockout)</li>
 *   <li>audita o evento (resource_id = email tentado)</li>
 *   <li>redireciona para a landing (drawer de login) com error=invalid (mensagem uniforme)</li>
 * </ul>
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptRepository attempts;
    private final AuditLogger audit;

    public LoginFailureHandler(LoginAttemptRepository attempts, AuditLogger audit) {
        this.attempts = attempts;
        this.audit = audit;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {

        String email = request.getParameter("username");
        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmail(email == null ? "" : email);
        attempt.setIpAddress(clientIp(request));
        attempt.setSuccess(false);
        attempt.setAttemptedAt(Instant.now());
        attempts.save(attempt);

        audit.record(null, "LOGIN_FAILURE", "users", email, request, null);

        String emailParam = email == null
                ? ""
                : URLEncoder.encode(email, StandardCharsets.UTF_8);
        // Login mora na landing: reabre o drawer de login com a mensagem de erro.
        response.sendRedirect("/?openLogin=login&error=invalid&email=" + emailParam);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
