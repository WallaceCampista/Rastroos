package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.rastroos.domain.entity.SupportTicket;
import com.rastroos.domain.entity.SupportTicketComment;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre com Postgres real as queries de suporte usadas na Etapa 12:
 * contadores por usuário, {@code adminSearch} com filtros nulos e não-nulos,
 * isolamento por usuário ({@code findByIdAndUserId}) e o thread de comentários.
 */
class SupportRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private SupportTicketRepository tickets;
    @Autowired private SupportTicketCommentRepository comments;

    @Test
    void contadoresPorUsuarioNaoVazam() {
        User alice = users.saveAndFlush(newUser("a-sup@example.com"));
        User bob = users.saveAndFlush(newUser("b-sup@example.com"));

        tickets.saveAndFlush(ticket("T-A1", alice, SupportTicketStatus.OPEN, "Bug do gráfico"));
        tickets.saveAndFlush(ticket("T-A2", alice, SupportTicketStatus.OPEN, "Outra coisa"));
        tickets.saveAndFlush(ticket("T-A3", alice, SupportTicketStatus.DONE, "Resolvido"));
        tickets.saveAndFlush(ticket("T-B1", bob, SupportTicketStatus.OPEN, "Do Bob"));

        assertThat(tickets.countByUserId(alice.getId())).isEqualTo(3L);
        assertThat(tickets.countByUserIdAndStatus(alice.getId(), SupportTicketStatus.OPEN)).isEqualTo(2L);
        assertThat(tickets.countByUserIdAndStatus(alice.getId(), SupportTicketStatus.DONE)).isEqualTo(1L);
    }

    @Test
    void adminSearchComFiltrosNulosRetornaTodos() {
        User alice = users.saveAndFlush(newUser("a-adm@example.com"));
        User bob = users.saveAndFlush(newUser("b-adm@example.com"));
        tickets.saveAndFlush(ticket("T-C1", alice, SupportTicketStatus.OPEN, "Login quebrado"));
        tickets.saveAndFlush(ticket("T-C2", bob, SupportTicketStatus.DONE, "Exportar CSV"));

        // "" = sem filtro de título (sentinela tipado)
        Page<SupportTicket> all = tickets.adminSearch("", null, PageRequest.of(0, 20));
        assertThat(all.getTotalElements()).isEqualTo(2L);

        // filtro por status
        Page<SupportTicket> open = tickets.adminSearch("", SupportTicketStatus.OPEN,
                PageRequest.of(0, 20));
        assertThat(open.getContent()).extracting(SupportTicket::getId).containsExactly("T-C1");

        // filtro por título (case-insensitive)
        Page<SupportTicket> byTitle = tickets.adminSearch("csv", null, PageRequest.of(0, 20));
        assertThat(byTitle.getContent()).extracting(SupportTicket::getId).containsExactly("T-C2");
    }

    @Test
    void findByIdAndUserIdIsolaEntreUsuarios() {
        User alice = users.saveAndFlush(newUser("a-iso@example.com"));
        User bob = users.saveAndFlush(newUser("b-iso@example.com"));
        tickets.saveAndFlush(ticket("T-D1", alice, SupportTicketStatus.OPEN, "Da Alice"));

        assertThat(tickets.findByIdAndUserId("T-D1", alice.getId())).isPresent();
        assertThat(tickets.findByIdAndUserId("T-D1", bob.getId())).isEmpty();
    }

    @Test
    void threadDeComentariosOrdenadoEContado() {
        User alice = users.saveAndFlush(newUser("a-thr@example.com"));
        tickets.saveAndFlush(ticket("T-E1", alice, SupportTicketStatus.OPEN, "Com respostas"));

        comments.saveAndFlush(comment("T-E1", alice, UserRole.USER,
                "primeira", Instant.parse("2026-05-01T10:00:00Z")));
        comments.saveAndFlush(comment("T-E1", alice, UserRole.ADMIN,
                "segunda", Instant.parse("2026-05-02T10:00:00Z")));

        assertThat(comments.countByTicketId("T-E1")).isEqualTo(2L);
        List<SupportTicketComment> thread = comments.findAllByTicketIdOrderByCreatedAtAsc("T-E1");
        assertThat(thread).extracting(SupportTicketComment::getBody)
                .containsExactly("primeira", "segunda");
    }

    // ── helpers ──────────────────────────────────────────────

    private static User newUser(String email) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private static SupportTicket ticket(String id, User owner,
                                        SupportTicketStatus status, String title) {
        SupportTicket t = new SupportTicket();
        t.setId(id);
        t.setUserId(owner.getId());
        t.setCategory(SupportTicketCategory.BUG);
        t.setTitle(title);
        t.setDescription("Descrição do chamado " + id);
        t.setPriority(SupportTicketPriority.MEDIUM);
        t.setStatus(status);
        return t;
    }

    private static SupportTicketComment comment(String ticketId, User author,
                                                UserRole role, String body, Instant at) {
        SupportTicketComment c = new SupportTicketComment();
        c.setTicketId(ticketId);
        c.setAuthorId(author.getId());
        c.setAuthorRole(role);
        c.setBody(body);
        c.setCreatedAt(at);
        return c;
    }
}
