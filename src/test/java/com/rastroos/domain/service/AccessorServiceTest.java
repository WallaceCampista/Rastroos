package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.AccessorRequest;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccessorRequestStatus;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccessorRequestRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.web.dto.AccessorSummaryDto;

@ExtendWith(MockitoExtension.class)
class AccessorServiceTest {

    @Mock private UserRepository users;
    @Mock private AccessorRequestRepository requests;

    private AccessorService service;

    @BeforeEach
    void setUp() {
        service = new AccessorService(users, requests);
    }

    // ── máscara de valores (privacidade do titular) ──────────

    @Test
    void setValuesMasked_acessorDoTitular_ligaFlagESalva() {
        UUID target = UUID.randomUUID();
        UUID accId = UUID.randomUUID();
        User acc = accessor(accId, target);
        when(users.findById(accId)).thenReturn(Optional.of(acc));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setValuesMasked(target, accId, true);

        assertThat(acc.isValuesMasked()).isTrue();
        verify(users).save(acc);
    }

    @Test
    void setValuesMasked_acessorDeOutroTitular_retorna404SemSalvar() {
        UUID target = UUID.randomUUID();
        UUID outroTitular = UUID.randomUUID();
        UUID accId = UUID.randomUUID();
        User acc = accessor(accId, outroTitular);          // pertence a outro titular
        when(users.findById(accId)).thenReturn(Optional.of(acc));

        assertThatThrownBy(() -> service.setValuesMasked(target, accId, true))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(users, never()).save(any());
    }

    @Test
    void setValuesMasked_contaNaoAcessor_retorna404() {
        UUID target = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        User comum = accessor(id, target);
        comum.setRole(UserRole.USER);                      // não é acessor
        when(users.findById(id)).thenReturn(Optional.of(comum));

        assertThatThrownBy(() -> service.setValuesMasked(target, id, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void myAccessors_mapeiaResumo() {
        UUID target = UUID.randomUUID();
        User acc = accessor(UUID.randomUUID(), target);
        acc.setValuesMasked(true);
        when(users.findByAccessesUserIdOrderByCreatedAtAsc(target)).thenReturn(List.of(acc));

        List<AccessorSummaryDto> list = service.myAccessors(target);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).email()).isEqualTo("ana@example.com");
        assertThat(list.get(0).valuesMasked()).isTrue();
    }

    // ── solicitações ─────────────────────────────────────────

    @Test
    void createRequest_normalizaESalvaPendente() {
        UUID me = UUID.randomUUID();
        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessorRequest r = service.createRequest(me, "  Ana  ", "  Ana@Example.com ", "  contadora ");

        assertThat(r.getRequesterUserId()).isEqualTo(me);
        assertThat(r.getAccessorName()).isEqualTo("Ana");
        assertThat(r.getAccessorEmail()).isEqualTo("ana@example.com");
        assertThat(r.getNote()).isEqualTo("contadora");
        assertThat(r.getStatus()).isEqualTo(AccessorRequestStatus.PENDING);
    }

    @Test
    void reject_solicitacaoPendente_marcaRejeitada() {
        UUID id = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        AccessorRequest r = new AccessorRequest();
        r.setId(id);
        r.setStatus(AccessorRequestStatus.PENDING);
        when(requests.findById(id)).thenReturn(Optional.of(r));
        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reject(id, admin);

        assertThat(r.getStatus()).isEqualTo(AccessorRequestStatus.REJECTED);
        assertThat(r.getResolvedBy()).isEqualTo(admin);
        assertThat(r.getResolvedAt()).isNotNull();
    }

    @Test
    void markApproved_vinculaContaCriadaEAprova() {
        UUID id = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        AccessorRequest r = new AccessorRequest();
        r.setId(id);
        r.setStatus(AccessorRequestStatus.PENDING);
        when(requests.findById(id)).thenReturn(Optional.of(r));
        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markApproved(id, admin, created);

        assertThat(r.getStatus()).isEqualTo(AccessorRequestStatus.APPROVED);
        assertThat(r.getCreatedUserId()).isEqualTo(created);
        assertThat(r.getResolvedBy()).isEqualTo(admin);
    }

    // ── helpers ──────────────────────────────────────────────

    private static User accessor(UUID id, UUID target) {
        User u = new User();
        u.setId(id);
        u.setName("Ana");
        u.setEmail("ana@example.com");
        u.setRole(UserRole.ACESSOR);
        u.setStatus(UserStatus.ACTIVE);
        u.setAccessesUserId(target);
        u.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        return u;
    }
}
