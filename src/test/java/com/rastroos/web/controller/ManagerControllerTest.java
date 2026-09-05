package com.rastroos.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.domain.service.ChatService;
import com.rastroos.domain.service.DashboardService;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.ChatMessageDto;
import com.rastroos.web.dto.ChatSummaryDto;
import com.rastroos.web.dto.ManagerView;

@WebMvcTest(controllers = ManagerController.class,
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
class ManagerControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ChatService service;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private DashboardService dashboard;

    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(userId);
    }

    private static DashboardModel emptySnapshot() {
        return new DashboardModel(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), 0L, 0L,
                new DashboardKpisDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void managerRenderizaBoasVindas() throws Exception {
        when(service.load(eq(userId), eq(null)))
                .thenReturn(new ManagerView(List.of(), null, List.of("Como estão meus gastos?")));

        mvc.perform(get("/app/manager"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/manager"))
                .andExpect(model().attribute("activeNav", "manager"))
                .andExpect(model().attributeExists("view", "promptForm"));
    }

    @Test
    void managerRenderizaConversaAtiva() throws Exception {
        UUID chatId = UUID.randomUUID();
        when(service.load(eq(userId), eq(chatId))).thenReturn(activeView(chatId));
        when(dashboard.load(eq(userId), any())).thenReturn(emptySnapshot());

        mvc.perform(get("/app/manager").param("chat", chatId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("app/manager"))
                .andExpect(model().attributeExists("canvasKpis", "canvasCats"));
    }

    @Test
    void managerComChatInvalidoCaiNoWelcome() throws Exception {
        when(service.load(eq(userId), eq(null)))
                .thenReturn(new ManagerView(List.of(), null, List.of()));

        mvc.perform(get("/app/manager").param("chat", "nao-e-uuid"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/manager"));
    }

    @Test
    void managerNaoRenderizaOWidgetFlutuante() throws Exception {
        when(service.load(eq(userId), eq(null)))
                .thenReturn(new ManagerView(List.of(), null, List.of("Como estão meus gastos?")));

        mvc.perform(get("/app/manager"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("alfredoWidget", false))
                .andExpect(content().string(not(containsString("data-alfredo"))))
                .andExpect(content().string(containsString("mgr-hero-orb")));
    }

    @Test
    void startValidoRedirecionaParaConversa() throws Exception {
        UUID chatId = UUID.randomUUID();
        when(service.start(eq(userId), eq("Quanto gastei?"))).thenReturn(chatId);

        mvc.perform(post("/app/manager/new").param("message", "Quanto gastei?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/manager?chat=" + chatId));

        verify(service).start(userId, "Quanto gastei?");
    }

    @Test
    void startVazioRedirecionaComErro() throws Exception {
        mvc.perform(post("/app/manager/new").param("message", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/manager"))
                .andExpect(flash().attribute("error", "chat.empty"));
    }

    @Test
    void sendRedirecionaParaConversa() throws Exception {
        UUID chatId = UUID.randomUUID();

        mvc.perform(post("/app/manager/{id}/messages", chatId).param("message", "e agora?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/manager?chat=" + chatId));

        verify(service).send(userId, chatId, "e agora?");
    }

    @Test
    void deleteRedirecionaComFlash() throws Exception {
        UUID chatId = UUID.randomUUID();

        mvc.perform(post("/app/manager/{id}/delete", chatId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/manager"))
                .andExpect(flash().attribute("ok", "chat.deleted"));

        verify(service).delete(userId, chatId);
    }

    // ── helpers ──────────────────────────────────────────────

    private ManagerView activeView(UUID chatId) {
        ChatDetailDto detail = new ChatDetailDto(chatId, "Quanto gastei?", List.of(
                new ChatMessageDto(ChatMessageRole.USER, "Quanto gastei?",
                        Instant.parse("2026-05-01T10:00:00Z"), false),
                new ChatMessageDto(ChatMessageRole.ASSISTANT, "Você gastou R$ 1.234.",
                        Instant.parse("2026-05-01T10:00:05Z"), true)));
        List<ChatSummaryDto> history = List.of(
                new ChatSummaryDto(chatId, "Quanto gastei?",
                        Instant.parse("2026-05-01T10:00:00Z"), true));
        return new ManagerView(history, detail, List.of("Onde economizar?"));
    }
}
