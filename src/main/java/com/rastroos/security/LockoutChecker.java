package com.rastroos.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.rastroos.domain.repository.LoginAttemptRepository;

/**
 * Avalia se um email está bloqueado por excesso de falhas recentes.
 * Janela e limite configuráveis em {@code application.yml}:
 *
 * <pre>
 * rastroos:
 *   security:
 *     lockout:
 *       max-failures: 5
 *       window-minutes: 15
 * </pre>
 */
@Component
public class LockoutChecker {

    private final LoginAttemptRepository attempts;

    @Value("${rastroos.security.lockout.max-failures:5}")
    private int maxFailures;

    @Value("${rastroos.security.lockout.window-minutes:15}")
    private int windowMinutes;

    public LockoutChecker(LoginAttemptRepository attempts) {
        this.attempts = attempts;
    }

    public boolean isLocked(String email) {
        if (email == null || email.isBlank()) return false;
        Instant since = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        return attempts.countFailuresSince(email, since) >= maxFailures;
    }

    public int getMaxFailures()   { return maxFailures; }
    public int getWindowMinutes() { return windowMinutes; }
}
