package com.rastroos.domain.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.SupportTicket;
import com.rastroos.domain.entity.SupportTicketComment;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.SupportTicketCommentRepository;
import com.rastroos.domain.repository.SupportTicketRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.web.dto.SupportListView;
import com.rastroos.web.dto.TicketCommentDto;
import com.rastroos.web.dto.TicketDetailView;
import com.rastroos.web.dto.TicketSummaryDto;
import com.rastroos.web.form.TicketForm;

/**
 * Regras de negócio dos chamados de suporte.
 *
 * <p>Isolamento por usuário: o usuário comum só enxerga os próprios chamados;
 * o admin enxerga todos. Acesso a chamado de outro usuário (sem ser admin) →
 * {@link ResourceNotFoundException} (HTTP 404), para não vazar existência.
 * Troca de status é exclusiva do admin.
 */
@Service
public class SupportService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Alfabeto sem caracteres ambíguos (0/O, 1/I) para o id legível. */
    private static final char[] ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int ID_LENGTH = 6;
    private static final int ID_MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SupportTicketRepository tickets;
    private final SupportTicketCommentRepository comments;
    private final UserRepository users;

    public SupportService(SupportTicketRepository tickets,
                          SupportTicketCommentRepository comments,
                          UserRepository users) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public SupportListView list(UUID currentUserId, boolean admin,
                                String statusStr, String priorityStr, String categoryStr,
                                boolean mine, String search,
                                int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        SupportTicketStatus status = parseStatus(statusStr);
        SupportTicketPriority priority = parsePriority(priorityStr);
        SupportTicketCategory category = parseCategory(categoryStr);
        // "" = sem filtro de título (sentinela tipado; ver search)
        String q = (search == null || search.isBlank()) ? "" : search.trim();

        // Não-admin sempre vê só os próprios; admin pode alternar (mine = "Meus chamados").
        boolean scopedToUser = !admin || mine;
        UUID scopeUserId = scopedToUser ? currentUserId : null;

        Page<SupportTicket> result = tickets.search(scopeUserId, status, priority, category, q, pageable);

        long open, inProgress, done, canceled;
        if (scopeUserId == null) {
            open = tickets.countByStatus(SupportTicketStatus.OPEN);
            inProgress = tickets.countByStatus(SupportTicketStatus.IN_PROGRESS);
            done = tickets.countByStatus(SupportTicketStatus.DONE);
            canceled = tickets.countByStatus(SupportTicketStatus.CANCELED);
        } else {
            open = tickets.countByUserIdAndStatus(scopeUserId, SupportTicketStatus.OPEN);
            inProgress = tickets.countByUserIdAndStatus(scopeUserId, SupportTicketStatus.IN_PROGRESS);
            done = tickets.countByUserIdAndStatus(scopeUserId, SupportTicketStatus.DONE);
            canceled = tickets.countByUserIdAndStatus(scopeUserId, SupportTicketStatus.CANCELED);
        }

        // Coluna "Solicitante" só na visão global (admin vendo tudo).
        boolean showRequester = admin && !scopedToUser;
        Map<UUID, String> emails = showRequester ? resolveRequesterEmails(result.getContent()) : Map.of();

        List<TicketSummaryDto> items = result.getContent().stream()
                .map(t -> new TicketSummaryDto(
                        t.getId(),
                        t.getCategory(),
                        t.getTitle(),
                        t.getPriority(),
                        t.getStatus(),
                        t.getCreatedAt(),
                        t.getUpdatedAt(),
                        comments.countByTicketId(t.getId()),
                        showRequester ? emails.get(t.getUserId()) : null))
                .toList();

        return new SupportListView(items, safePage, safeSize,
                result.getTotalElements(), result.getTotalPages(),
                open, inProgress, done, canceled, admin);
    }

    @Transactional(readOnly = true)
    public TicketDetailView detail(UUID currentUserId, boolean admin, String ticketId) {
        SupportTicket t = requireVisible(currentUserId, admin, ticketId);

        List<SupportTicketComment> raw = comments.findAllByTicketIdOrderByCreatedAtAsc(ticketId);
        Map<UUID, String> emails = resolveAuthorEmails(raw, t.getUserId());

        List<TicketCommentDto> thread = new ArrayList<>(raw.size());
        for (SupportTicketComment c : raw) {
            thread.add(new TicketCommentDto(
                    c.getAuthorRole(),
                    emails.get(c.getAuthorId()),
                    c.getBody(),
                    c.getCreatedAt(),
                    c.getAuthorId().equals(currentUserId)));
        }

        boolean answerable = isAnswerable(t.getStatus());
        String requesterEmail = admin ? emails.get(t.getUserId()) : null;

        return new TicketDetailView(
                t.getId(), t.getCategory(), t.getTitle(), t.getDescription(),
                t.getPriority(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(),
                requesterEmail, thread, admin,
                answerable,
                !admin && answerable);
    }

    @Transactional
    public SupportTicket create(UUID userId, TicketForm form) {
        SupportTicket t = new SupportTicket();
        t.setId(generateId());
        t.setUserId(userId);
        t.setCategory(form.getCategory());
        t.setTitle(form.getTitle().trim());
        t.setDescription(form.getDescription().trim());
        t.setPriority(form.getPriority());
        t.setStatus(SupportTicketStatus.OPEN);
        return tickets.save(t);
    }

    @Transactional
    public SupportTicketComment addComment(UUID currentUserId, boolean admin,
                                           String ticketId, String body) {
        SupportTicket t = requireVisible(currentUserId, admin, ticketId);
        if (!isAnswerable(t.getStatus())) {
            throw new IllegalStateException("support.closed");
        }
        SupportTicketComment c = new SupportTicketComment();
        c.setTicketId(ticketId);
        c.setAuthorId(currentUserId);
        c.setAuthorRole(admin ? UserRole.ADMIN : UserRole.USER);
        c.setBody(body.trim());
        comments.save(c);

        // reabrir/atualizar: uma resposta do admin coloca em andamento
        if (admin && t.getStatus() == SupportTicketStatus.OPEN) {
            t.setStatus(SupportTicketStatus.IN_PROGRESS);
        }
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        return c;
    }

    /** Troca de status é ação exclusiva do admin (defesa em profundidade). */
    @Transactional
    public SupportTicket updateStatus(boolean admin, String ticketId, SupportTicketStatus status) {
        if (!admin) {
            throw new AccessDeniedException("support.adminOnly");
        }
        SupportTicket t = tickets.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("support.notFound"));
        t.setStatus(status);
        return tickets.save(t);
    }

    /** O dono cancela o próprio chamado enquanto ele estiver em aberto. */
    @Transactional
    public SupportTicket cancel(UUID currentUserId, boolean admin, String ticketId) {
        SupportTicket t = requireVisible(currentUserId, admin, ticketId);
        if (!isAnswerable(t.getStatus())) {
            throw new IllegalStateException("support.closed");
        }
        t.setStatus(SupportTicketStatus.CANCELED);
        return tickets.save(t);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private SupportTicket requireVisible(UUID currentUserId, boolean admin, String ticketId) {
        Optional<SupportTicket> found = admin
                ? tickets.findById(ticketId)
                : tickets.findByIdAndUserId(ticketId, currentUserId);
        return found.orElseThrow(() -> new ResourceNotFoundException("support.notFound"));
    }

    private static boolean isAnswerable(SupportTicketStatus status) {
        return status == SupportTicketStatus.OPEN || status == SupportTicketStatus.IN_PROGRESS;
    }

    private String generateId() {
        for (int attempt = 0; attempt < ID_MAX_ATTEMPTS; attempt++) {
            String id = "T-" + randomToken();
            if (!tickets.existsById(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Could not allocate a unique ticket id");
    }

    private static String randomToken() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ID_ALPHABET[RANDOM.nextInt(ID_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private Map<UUID, String> resolveRequesterEmails(List<SupportTicket> ts) {
        Set<UUID> ids = new HashSet<>();
        for (SupportTicket t : ts) ids.add(t.getUserId());
        return emailsById(ids);
    }

    private Map<UUID, String> resolveAuthorEmails(List<SupportTicketComment> cs, UUID requesterId) {
        Set<UUID> ids = new HashSet<>();
        ids.add(requesterId);
        for (SupportTicketComment c : cs) ids.add(c.getAuthorId());
        return emailsById(ids);
    }

    private Map<UUID, String> emailsById(Set<UUID> ids) {
        Map<UUID, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (User u : users.findAllById(ids)) {
            map.put(u.getId(), u.getEmail());
        }
        return map;
    }

    private static SupportTicketStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return SupportTicketStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SupportTicketPriority parsePriority(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return SupportTicketPriority.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SupportTicketCategory parseCategory(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return SupportTicketCategory.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
