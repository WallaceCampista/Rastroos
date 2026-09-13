package com.rastroos.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate-limit por usuário nas rotas que consomem IA (perguntas ao Alfredo e
 * leitura de documento por visão).
 *
 * <p>Complementa o teto diário ({@code AiBudgetGuard}), que limita o
 * <em>total</em> do dia: aqui a proteção é contra a <em>rajada</em> — um script
 * (ou um clique repetido num botão travado) disparando centenas de perguntas em
 * segundos. Sem isso, o teto diário inteiro seria queimado em um minuto.
 *
 * <p>A chave é o usuário autenticado, não o IP: dinheiro é gasto por conta, e
 * várias pessoas atrás do mesmo IP não devem disputar a mesma cota.
 *
 * <pre>
 * rastroos:
 *   security:
 *     rate-limit:
 *       ai-requests-per-window: 20
 *       ai-window-minutes: 1
 * </pre>
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    /** Teto de contas rastreadas; ao estourar, o mapa é reciclado. */
    private static final int MAX_TRACKED = 10_000;

    @Value("${rastroos.security.rate-limit.ai-requests-per-window:20}")
    private int requestsPerWindow;

    @Value("${rastroos.security.rate-limit.ai-window-minutes:1}")
    private int windowMinutes;

    private final CurrentUser currentUser;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AiRateLimitFilter(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/chats")
              || (uri.startsWith("/api/v1/insights") && uri.endsWith("/chat"))
              || uri.startsWith("/app/manager/new")
              || uri.endsWith("/messages")
              || uri.startsWith("/app/expenses/extract")
              || (uri.startsWith("/app/cards/") && uri.endsWith("/invoice/extract")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = currentUser.id().map(java.util.UUID::toString).orElse(null);
        if (key == null) {
            // Sem usuário no contexto não há o que limitar aqui: a rota já é
            // autenticada, e quem barra anônimo é o Spring Security.
            chain.doFilter(request, response);
            return;
        }

        if (!bucketFor(key).tryConsume(1)) {
            writeTooManyRequests(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** JSON para o widget, texto para os formulários — cada um sabe ler o seu. */
    private void writeTooManyRequests(HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, windowMinutes) * 60));
        boolean wantsJson = request.getRequestURI().startsWith("/api/")
                || MediaType.APPLICATION_JSON_VALUE.equals(request.getHeader("Accept"));
        if (wantsJson) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"code":"AI_RATE_LIMITED",\
                    "message":"Muitas perguntas em pouco tempo. Aguarde alguns instantes."}""");
        } else {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("Muitas requisições. Tente novamente em instantes.");
        }
    }

    private Bucket bucketFor(String userId) {
        if (buckets.size() >= MAX_TRACKED) {
            buckets.clear();   // balde é descartável: no pior caso alguém ganha uma janela nova
        }
        return buckets.computeIfAbsent(userId, key -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerWindow)
                        .refillIntervally(requestsPerWindow, Duration.ofMinutes(windowMinutes))
                        .build())
                .build());
    }

    /** Exposto para os testes verificarem a configuração aplicada. */
    Map<String, Bucket> trackedBuckets() {
        return Map.copyOf(buckets);
    }
}
