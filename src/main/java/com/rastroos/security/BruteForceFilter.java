package com.rastroos.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate-limit por IP nas rotas de autenticação. Anti brute force camada 1.
 * Configurável:
 *
 * <pre>
 * rastroos:
 *   security:
 *     rate-limit:
 *       auth-requests-per-window: 10
 *       auth-window-minutes: 15
 * </pre>
 *
 * <p>Não distingue email (isso é feito pelo {@link LockoutChecker}). Aqui o
 * objetivo é impedir ataque distribuído por IP único.
 */
@Component
public class BruteForceFilter extends OncePerRequestFilter {

    @Value("${rastroos.security.rate-limit.auth-requests-per-window:10}")
    private int requestsPerWindow;

    @Value("${rastroos.security.rate-limit.auth-window-minutes:15}")
    private int windowMinutes;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public BruteForceFilter() {
    }

    private Bucket bucketFor(String ip) {
        return buckets.computeIfAbsent(ip, key -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerWindow)
                        .refillIntervally(requestsPerWindow,
                                          Duration.ofMinutes(windowMinutes))
                        .build())
                .build());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) return true;
        String uri = request.getRequestURI();
        return !(uri.startsWith("/auth/login")
              || uri.startsWith("/auth/signup")
              || uri.startsWith("/auth/forgot"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = clientIp(request);
        if (!bucketFor(ip).tryConsume(1)) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowMinutes * 60));
            response.getWriter().write("Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    // visível para testes
    void resetForTests() { buckets.clear(); }
}
