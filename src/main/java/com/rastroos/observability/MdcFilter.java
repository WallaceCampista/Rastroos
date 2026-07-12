package com.rastroos.observability;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.rastroos.security.CustomUserDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Popula o MDC com {@code traceId} (por requisição) e {@code userId} (quando
 * autenticado), para que os logs estruturados (JSON) correlacionem eventos.
 * Registrado <em>depois</em> da cadeia do Spring Security (ver
 * {@link com.rastroos.config.ObservabilityConfig}) para que o
 * {@code SecurityContext} já esteja preenchido.
 *
 * <p>O {@code traceId} reaproveita um {@code X-Request-Id} de entrada (se vier
 * de um proxy/gateway) ou gera um novo, e é ecoado no header da resposta. Nunca
 * loga dados sensíveis — apenas os identificadores acima.
 */
public class MdcFilter extends OncePerRequestFilter {

    static final String TRACE_ID = "traceId";
    static final String USER_ID = "userId";
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);

        String userId = currentUserId();
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank() && incoming.length() <= 64) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }

    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof CustomUserDetails details && details.getId() != null) {
            return details.getId().toString();
        }
        return null;
    }
}
