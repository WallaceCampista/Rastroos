package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

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
import com.rastroos.web.dto.TicketDetailView;
import com.rastroos.web.form.TicketForm;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock private SupportTicketRepository tickets;
    @Mock private SupportTicketCommentRepository comments;
    @Mock private UserRepository users;

    @InjectMocks private SupportService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void createGeraIdEComecaAberto() {
        when(tickets.existsById(anyString())).thenReturn(false);
        when(tickets.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketForm form = new TicketForm();
        form.setCategory(SupportTicketCategory.BUG);
        form.setPriority(SupportTicketPriority.HIGH);
        form.setTitle("  Erro no gráfico  ");
        form.setDescription("  A linha some  ");

        SupportTicket saved = service.create(alice, form);

        assertThat(saved.getId()).startsWith("T-");
        assertThat(saved.getUserId()).isEqualTo(alice);
        assertThat(saved.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(saved.getTitle()).isEqualTo("Erro no gráfico");
        assertThat(saved.getDescription()).isEqualTo("A linha some");
    }

    @Test
    void listComoUsuarioUsaContadoresProprioSemVazarEmail() {
        SupportTicket t = ticket("T-AAA111", alice, SupportTicketStatus.OPEN);
        when(tickets.search(eq(alice), any(), any(), any(), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t), PageRequest.of(0, 20), 1));
        when(comments.countByTicketId("T-AAA111")).thenReturn(3L);
        when(tickets.countByUserIdAndStatus(alice, SupportTicketStatus.OPEN)).thenReturn(2L);
        when(tickets.countByUserIdAndStatus(alice, SupportTicketStatus.IN_PROGRESS)).thenReturn(1L);
        when(tickets.countByUserIdAndStatus(alice, SupportTicketStatus.DONE)).thenReturn(4L);

        SupportListView view = service.list(alice, false, null, null, null, false, null, 0, 20);

        assertThat(view.admin()).isFalse();
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).commentCount()).isEqualTo(3L);
        assertThat(view.items().get(0).requesterEmail()).isNull();
        assertThat(view.openCount()).isEqualTo(2L);
        assertThat(view.doneCount()).isEqualTo(4L);
        verify(tickets, never()).adminSearch(any(), any(), any());
    }

    @Test
    void listComoAdminUsaBuscaGlobalEResolveEmailDoSolicitante() {
        SupportTicket t = ticket("T-BBB222", bob, SupportTicketStatus.OPEN);
        // sem filtro de título → sentinela "" (não null); userId null = busca global
        when(tickets.search(any(), any(), any(), any(), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t), PageRequest.of(0, 20), 1));
        when(comments.countByTicketId("T-BBB222")).thenReturn(0L);
        when(tickets.countByStatus(any())).thenReturn(0L);
        User bobUser = user(bob, "bob@example.com");
        when(users.findAllById(any())).thenReturn(List.of(bobUser));

        SupportListView view = service.list(alice, true, null, null, null, false, null, 0, 20);

        assertThat(view.admin()).isTrue();
        assertThat(view.items().get(0).requesterEmail()).isEqualTo("bob@example.com");
        verify(tickets, never()).findAllByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void detailDeOutroUsuarioSemAdminRetorna404() {
        when(tickets.findByIdAndUserId("T-ZZZ999", alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(alice, false, "T-ZZZ999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void detailComoDonoMapeiaThreadEMarcaMine() {
        SupportTicket t = ticket("T-CCC333", alice, SupportTicketStatus.OPEN);
        when(tickets.findByIdAndUserId("T-CCC333", alice)).thenReturn(Optional.of(t));

        SupportTicketComment mine = comment("T-CCC333", alice, UserRole.USER, "minha dúvida");
        SupportTicketComment admin = comment("T-CCC333", bob, UserRole.ADMIN, "resposta");
        when(comments.findAllByTicketIdOrderByCreatedAtAsc("T-CCC333"))
                .thenReturn(List.of(mine, admin));
        when(users.findAllById(any())).thenReturn(List.of(
                user(alice, "alice@example.com"), user(bob, "admin@example.com")));

        TicketDetailView view = service.detail(alice, false, "T-CCC333");

        assertThat(view.comments()).hasSize(2);
        assertThat(view.comments().get(0).mine()).isTrue();
        assertThat(view.comments().get(1).authorRole()).isEqualTo(UserRole.ADMIN);
        assertThat(view.canComment()).isTrue();     // OPEN
        assertThat(view.canCancel()).isTrue();       // dono + aberto
        assertThat(view.requesterEmail()).isNull();  // não-admin não vê
    }

    @Test
    void addCommentEmChamadoFechadoFalha() {
        SupportTicket done = ticket("T-DDD444", alice, SupportTicketStatus.DONE);
        when(tickets.findByIdAndUserId("T-DDD444", alice)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.addComment(alice, false, "T-DDD444", "oi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("support.closed");
        verify(comments, never()).save(any());
    }

    @Test
    void addCommentDoAdminEmAbertoColocaEmAndamento() {
        SupportTicket open = ticket("T-EEE555", bob, SupportTicketStatus.OPEN);
        when(tickets.findById("T-EEE555")).thenReturn(Optional.of(open));
        when(comments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SupportTicketComment> captor = ArgumentCaptor.forClass(SupportTicketComment.class);

        service.addComment(alice, true, "T-EEE555", "  vendo isso  ");

        verify(comments).save(captor.capture());
        assertThat(captor.getValue().getAuthorRole()).isEqualTo(UserRole.ADMIN);
        assertThat(captor.getValue().getBody()).isEqualTo("vendo isso");
        assertThat(open.getStatus()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        verify(tickets).save(open);
    }

    @Test
    void updateStatusNaoAdminEhBloqueado() {
        assertThatThrownBy(() -> service.updateStatus(false, "T-FFF666", SupportTicketStatus.DONE))
                .isInstanceOf(AccessDeniedException.class);
        verify(tickets, never()).findById(anyString());
    }

    @Test
    void updateStatusComoAdminAplica() {
        SupportTicket t = ticket("T-GGG777", bob, SupportTicketStatus.OPEN);
        when(tickets.findById("T-GGG777")).thenReturn(Optional.of(t));
        when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket out = service.updateStatus(true, "T-GGG777", SupportTicketStatus.DONE);

        assertThat(out.getStatus()).isEqualTo(SupportTicketStatus.DONE);
    }

    @Test
    void cancelDoDonoEmAbertoMarcaCancelado() {
        SupportTicket t = ticket("T-HHH888", alice, SupportTicketStatus.IN_PROGRESS);
        when(tickets.findByIdAndUserId("T-HHH888", alice)).thenReturn(Optional.of(t));
        when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(alice, false, "T-HHH888");

        assertThat(t.getStatus()).isEqualTo(SupportTicketStatus.CANCELED);
    }

    @Test
    void cancelDeChamadoJaFechadoFalha() {
        SupportTicket t = ticket("T-III999", alice, SupportTicketStatus.CANCELED);
        when(tickets.findByIdAndUserId("T-III999", alice)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.cancel(alice, false, "T-III999"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── helpers ──────────────────────────────────────────────

    private static SupportTicket ticket(String id, UUID userId, SupportTicketStatus status) {
        SupportTicket t = new SupportTicket();
        t.setId(id);
        t.setUserId(userId);
        t.setCategory(SupportTicketCategory.BUG);
        t.setTitle("Título");
        t.setDescription("Descrição");
        t.setPriority(SupportTicketPriority.MEDIUM);
        t.setStatus(status);
        t.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        t.setUpdatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        return t;
    }

    private static SupportTicketComment comment(String ticketId, UUID authorId,
                                                UserRole role, String body) {
        SupportTicketComment c = new SupportTicketComment();
        c.setId(UUID.randomUUID());
        c.setTicketId(ticketId);
        c.setAuthorId(authorId);
        c.setAuthorRole(role);
        c.setBody(body);
        c.setCreatedAt(Instant.parse("2026-05-02T10:00:00Z"));
        return c;
    }

    private static User user(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }
}
