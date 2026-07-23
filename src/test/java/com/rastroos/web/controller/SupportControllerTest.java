package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.SupportTicket;
import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.service.SupportService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.SupportListView;
import com.rastroos.web.dto.TicketCommentDto;
import com.rastroos.web.dto.TicketDetailView;
import com.rastroos.web.dto.TicketSummaryDto;

@WebMvcTest(controllers = SupportController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        BruteForceFilter.class,
                        LockoutPreAuthFilter.class,
                        LockoutChecker.class,
                        LoginSuccessHandler.class,
                        LoginFailureHandler.class,
                        CustomUserDetailsService.class,
                        AuditLogger.class,
                        TopbarChipsInterceptor.class
                }))
@AutoConfigureMockMvc(addFilters = false)
class SupportControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private SupportService service;
    @MockitoBean private CurrentUser currentUser;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(userId);
        when(currentUser.isAdmin()).thenReturn(false);
    }

    @Test
    void listRenderizaTabelaComoUsuario() throws Exception {
        when(service.list(eq(userId), eq(false), any(), any(), eq(0), eq(20)))
                .thenReturn(listView(false));

        mvc.perform(get("/app/support"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/support"))
                .andExpect(model().attribute("activeNav", "support"))
                .andExpect(model().attributeExists("view", "filter", "statuses"));
    }

    @Test
    void listRenderizaColunaSolicitanteComoAdmin() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(service.list(eq(userId), eq(true), any(), any(), eq(0), eq(20)))
                .thenReturn(listView(true));

        mvc.perform(get("/app/support"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/support"));
    }

    @Test
    void newFormRenderiza() throws Exception {
        mvc.perform(get("/app/support/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/support-form"))
                .andExpect(model().attributeExists("ticketForm", "categories", "priorities"));
    }

    @Test
    void createValidoRedirecionaComFlash() throws Exception {
        when(service.create(eq(userId), any())).thenReturn(new SupportTicket());

        mvc.perform(post("/app/support/new")
                        .param("category", "BUG")
                        .param("priority", "MEDIUM")
                        .param("title", "Título de teste")
                        .param("description", "Descrição suficientemente longa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/support"))
                .andExpect(flash().attribute("ok", "support.created"));

        verify(service).create(eq(userId), any());
    }

    @Test
    void createInvalidoVoltaAoForm() throws Exception {
        mvc.perform(post("/app/support/new")
                        .param("category", "BUG")
                        .param("priority", "MEDIUM")
                        .param("title", "x")   // < 3
                        .param("description", "y"))  // < 5
                .andExpect(status().isOk())
                .andExpect(view().name("app/support-form"));
    }

    @Test
    void detailRenderizaThread() throws Exception {
        when(service.detail(eq(userId), eq(false), eq("T-ABC123")))
                .thenReturn(detailView(false, true));

        mvc.perform(get("/app/support/T-ABC123"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/support-detail"))
                .andExpect(model().attributeExists("view", "commentForm", "statusForm", "statuses"));
    }

    @Test
    void detailComoAdminRenderizaFormDeStatus() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(service.detail(eq(userId), eq(true), eq("T-ABC123")))
                .thenReturn(detailView(true, true));

        mvc.perform(get("/app/support/T-ABC123"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/support-detail"));
    }

    @Test
    void commentRedirecionaParaODetalhe() throws Exception {
        mvc.perform(post("/app/support/T-ABC123/comments").param("body", "minha resposta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/support/T-ABC123"))
                .andExpect(flash().attribute("ok", "support.commentAdded"));

        verify(service).addComment(eq(userId), anyBoolean(), eq("T-ABC123"), eq("minha resposta"));
    }

    @Test
    void changeStatusRedireciona() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        mvc.perform(post("/app/support/T-ABC123/status").param("status", "DONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/support/T-ABC123"))
                .andExpect(flash().attribute("ok", "support.statusUpdated"));

        verify(service).updateStatus(eq(true), eq("T-ABC123"), eq(SupportTicketStatus.DONE));
    }

    @Test
    void cancelRedireciona() throws Exception {
        mvc.perform(post("/app/support/T-ABC123/cancel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/support/T-ABC123"))
                .andExpect(flash().attribute("ok", "support.canceled"));

        verify(service).cancel(eq(userId), eq(false), eq("T-ABC123"));
    }

    // ── helpers ──────────────────────────────────────────────

    private SupportListView listView(boolean admin) {
        TicketSummaryDto row = new TicketSummaryDto(
                "T-ABC123", SupportTicketCategory.BUG, "Erro no gráfico",
                SupportTicketPriority.HIGH, SupportTicketStatus.OPEN,
                Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-02T10:00:00Z"),
                2L, admin ? "bob@example.com" : null);
        return new SupportListView(List.of(row), 0, 20, 1, 1, 1, 0, 0, admin);
    }

    private TicketDetailView detailView(boolean admin, boolean open) {
        TicketCommentDto c1 = new TicketCommentDto(UserRole.USER, "alice@example.com",
                "minha dúvida", Instant.parse("2026-05-02T10:00:00Z"), true);
        TicketCommentDto c2 = new TicketCommentDto(UserRole.ADMIN, "admin@example.com",
                "estamos vendo", Instant.parse("2026-05-03T10:00:00Z"), false);
        SupportTicketStatus st = open ? SupportTicketStatus.OPEN : SupportTicketStatus.DONE;
        return new TicketDetailView(
                "T-ABC123", SupportTicketCategory.BUG, "Erro no gráfico",
                "A linha do gráfico some ao trocar de mês.",
                SupportTicketPriority.HIGH, st,
                Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-03T10:00:00Z"),
                admin ? "bob@example.com" : null, List.of(c1, c2), admin, open, !admin && open);
    }
}
