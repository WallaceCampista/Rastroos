package com.rastroos.domain.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.AccessorRequest;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccessorRequestStatus;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccessorRequestRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.web.dto.AccessorRequestDto;
import com.rastroos.web.dto.AccessorSummaryDto;

/**
 * Domínio do perfil acessor na perspectiva do <em>titular</em> (transparência
 * e privacidade) e do <em>admin</em> (fila de solicitações). Isolamento estrito:
 * o titular só enxerga/altera acessores vinculados à própria conta; acesso a id
 * de outro → {@link ResourceNotFoundException} (404, não vaza existência).
 */
@Service
public class AccessorService {

    private final UserRepository users;
    private final AccessorRequestRepository requests;

    public AccessorService(UserRepository users, AccessorRequestRepository requests) {
        this.users = users;
        this.requests = requests;
    }

    // ── Titular: transparência + privacidade ─────────────────────────────

    /** Acessores vinculados a este titular (para ele ver quantos/quem tem). */
    @Transactional(readOnly = true)
    public List<AccessorSummaryDto> myAccessors(UUID targetId) {
        return users.findByAccessesUserIdOrderByCreatedAtAsc(targetId).stream()
                .map(u -> new AccessorSummaryDto(u.getId(), u.getName(), u.getEmail(),
                        u.getStatus(), u.isValuesMasked(), u.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countMyAccessors(UUID targetId) {
        return users.countByAccessesUserId(targetId);
    }

    /**
     * Liga/desliga a máscara de valores para um acessor DESTE titular. Valida a
     * posse: o alvo do acessor precisa ser exatamente {@code targetId}.
     */
    @Transactional
    public void setValuesMasked(UUID targetId, UUID accessorId, boolean masked) {
        User accessor = users.findById(accessorId)
                .filter(u -> u.getRole() == UserRole.ACESSOR
                        && targetId.equals(u.getAccessesUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("accessor.notFound"));
        accessor.setValuesMasked(masked);
        users.save(accessor);
    }

    // ── Titular: solicitações ────────────────────────────────────────────

    @Transactional
    public AccessorRequest createRequest(UUID requesterId, String name, String email, String note) {
        AccessorRequest r = new AccessorRequest();
        r.setRequesterUserId(requesterId);
        r.setAccessorName(name.trim());
        r.setAccessorEmail(email.trim().toLowerCase());
        r.setNote(note == null || note.isBlank() ? null : note.trim());
        r.setStatus(AccessorRequestStatus.PENDING);
        return requests.save(r);
    }

    @Transactional(readOnly = true)
    public List<AccessorRequestDto> myRequests(UUID requesterId) {
        return requests.findByRequesterUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(r -> new AccessorRequestDto(r.getId(), r.getRequesterUserId(), null, null,
                        r.getAccessorName(), r.getAccessorEmail(), r.getNote(), r.getStatus(),
                        r.getCreatedAt(), r.getResolvedAt(), r.getCreatedUserId()))
                .toList();
    }

    // ── Admin: fila de solicitações ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccessorRequestDto> pendingRequests() {
        return requests.findByStatusOrderByCreatedAtAsc(AccessorRequestStatus.PENDING).stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return requests.countByStatus(AccessorRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public AccessorRequest requireRequest(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("accessor.request.notFound"));
    }

    @Transactional
    public void reject(UUID id, UUID adminId) {
        AccessorRequest r = requireRequest(id);
        if (r.getStatus() == AccessorRequestStatus.PENDING) {
            r.setStatus(AccessorRequestStatus.REJECTED);
            r.setResolvedAt(Instant.now());
            r.setResolvedBy(adminId);
            requests.save(r);
        }
    }

    /** Vincula a conta acessor recém-criada e marca a solicitação como aprovada. */
    @Transactional
    public void markApproved(UUID id, UUID adminId, UUID createdUserId) {
        requests.findById(id).ifPresent(r -> {
            r.setStatus(AccessorRequestStatus.APPROVED);
            r.setResolvedAt(Instant.now());
            r.setResolvedBy(adminId);
            r.setCreatedUserId(createdUserId);
            requests.save(r);
        });
    }

    private AccessorRequestDto toAdminDto(AccessorRequest r) {
        User requester = users.findById(r.getRequesterUserId()).orElse(null);
        return new AccessorRequestDto(
                r.getId(), r.getRequesterUserId(),
                requester == null ? null : requester.getName(),
                requester == null ? null : requester.getEmail(),
                r.getAccessorName(), r.getAccessorEmail(), r.getNote(),
                r.getStatus(), r.getCreatedAt(), r.getResolvedAt(), r.getCreatedUserId());
    }
}
