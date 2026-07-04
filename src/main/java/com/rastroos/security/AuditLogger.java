package com.rastroos.security;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rastroos.domain.entity.AuditLog;
import com.rastroos.domain.repository.AuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Façade para gravar eventos auditáveis. Nunca persiste payload sensível
 * (senha, hash, código de verificação). {@code userId} pode ser null para
 * eventos pré-autenticação.
 */
@Component
public class AuditLogger {

    private final AuditLogRepository repo;

    public AuditLogger(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(UUID userId, String action, String resource, String resourceId,
                       HttpServletRequest request, String detailsJsonOrNull) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setResource(resource);
        entry.setResourceId(resourceId);
        entry.setIpAddress(request != null ? clientIp(request) : null);
        entry.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        entry.setDetails(detailsJsonOrNull);
        repo.save(entry);
    }

    public void record(UUID userId, String action, HttpServletRequest request) {
        record(userId, action, null, null, request, null);
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
