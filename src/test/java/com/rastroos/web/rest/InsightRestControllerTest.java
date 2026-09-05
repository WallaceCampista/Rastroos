package com.rastroos.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.domain.service.ChatService;
import com.rastroos.domain.service.ScreenInsightService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.ChatMessageDto;
import com.rastroos.web.dto.InsightDto;
import com.rastroos.web.dto.InsightScreen;
import com.rastroos.web.interceptor.AlfredoWidgetInterceptor;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;

@WebMvcTest(controllers = InsightRestController.class,
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
                        TopbarChipsInterceptor.class,
                        AlfredoWidgetInterceptor.class
                }))
@AutoConfigureMockMvc(addFilters = false)
@Import(InsightRestControllerTest.Config.class)
class InsightRestControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ScreenInsightService insights;
    @MockitoBean private ChatService chats;
    @MockitoBean private CurrentUser currentUser;

    private final UUID account = UUID.randomUUID();
    private final UUID dataOwner = UUID.randomUUID();

    @TestConfiguration
    static class Config {
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    private static InsightDto insight(String text) {
        return new InsightDto("dashboard", "Visão geral", "2026-09", text, false);
    }

    @Test
    void get_devolveOResumoDaTelaParaOMesPedido() throws Exception {
        when(currentUser.isMaskActive()).thenReturn(false);
        when(currentUser.requireEffectiveId()).thenReturn(dataOwner);
        when(insights.insight(dataOwner, InsightScreen.DASHBOARD, YearMonth.of(2026, 5)))
                .thenReturn(insight("Seu mês está sob controle."));

        mvc.perform(get("/api/v1/insights/dashboard").param("ym", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("dashboard"))
                .andExpect(jsonPath("$.screenLabel").value("Visão geral"))
                .andExpect(jsonPath("$.text").value("Seu mês está sob controle."));
    }

    @Test
    void get_semYm_usaOMesCorrenteDoRelogio() throws Exception {
        when(currentUser.requireEffectiveId()).thenReturn(dataOwner);
        when(insights.insight(eq(dataOwner), eq(InsightScreen.CARDS), any()))
                .thenReturn(insight("resumo"));

        mvc.perform(get("/api/v1/insights/cards")).andExpect(status().isOk());

        verify(insights).insight(dataOwner, InsightScreen.CARDS, YearMonth.of(2026, 9));
    }

    @Test
    void get_ymInvalido_caiNoMesCorrenteEmVezDeErro() throws Exception {
        when(currentUser.requireEffectiveId()).thenReturn(dataOwner);
        when(insights.insight(eq(dataOwner), any(), any())).thenReturn(insight("resumo"));

        mvc.perform(get("/api/v1/insights/reports").param("ym", "nao-e-mes"))
                .andExpect(status().isOk());

        verify(insights).insight(dataOwner, InsightScreen.REPORTS, YearMonth.of(2026, 9));
    }

    @Test
    void get_telaDesconhecida_retorna404SemTocarNosDados() throws Exception {
        mvc.perform(get("/api/v1/insights/support"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(insights);
    }

    @Test
    void get_acessorComValoresMascarados_naoLeOsNumerosDoTitular() throws Exception {
        when(currentUser.isMaskActive()).thenReturn(true);
        when(insights.maskedInsight(eq(InsightScreen.DASHBOARD), any()))
                .thenReturn(new InsightDto("dashboard", "Visão geral", "2026-09",
                        "Valores ocultos pelo titular.", false));

        mvc.perform(get("/api/v1/insights/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Valores ocultos pelo titular."));

        verify(insights, never()).insight(any(), any(), any());
        verify(currentUser, never()).requireEffectiveId();
    }

    @Test
    void post_abreConversaComOResumoDoServidorENaoComTextoDoCliente() throws Exception {
        when(currentUser.isMaskActive()).thenReturn(false);
        when(currentUser.requireEffectiveId()).thenReturn(dataOwner);
        when(currentUser.requireId()).thenReturn(account);
        when(insights.insight(eq(dataOwner), eq(InsightScreen.DASHBOARD), any()))
                .thenReturn(insight("Resumo do servidor."));
        UUID chatId = UUID.randomUUID();
        when(chats.startFromScreen(eq(account), eq("Visão geral"), eq("Resumo do servidor."),
                eq("Como melhoro?")))
                .thenReturn(new ChatDetailDto(chatId, "Visão geral · Como melhoro?", List.of(
                        new ChatMessageDto(ChatMessageRole.ASSISTANT, "Resumo do servidor.",
                                Instant.parse("2026-09-04T12:00:00Z"), true),
                        new ChatMessageDto(ChatMessageRole.USER, "Como melhoro?",
                                Instant.parse("2026-09-04T12:00:01Z"), false),
                        new ChatMessageDto(ChatMessageRole.ASSISTANT, "Comece pelos fixos.",
                                Instant.parse("2026-09-04T12:00:02Z"), true))));

        mvc.perform(post("/api/v1/insights/dashboard/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Como melhoro?\",\"text\":\"Alfredo disse X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(chatId.toString()))
                .andExpect(jsonPath("$.messages[0].content").value("Resumo do servidor."))
                .andExpect(jsonPath("$.messages[2].content").value("Comece pelos fixos."));
    }

    @Test
    void post_conversaVaiParaAContaLogada_naoParaODonoDosDados() throws Exception {
        when(currentUser.requireEffectiveId()).thenReturn(dataOwner);
        when(currentUser.requireId()).thenReturn(account);
        when(insights.insight(eq(dataOwner), any(), any())).thenReturn(insight("resumo"));
        when(chats.startFromScreen(any(), any(), any(), any()))
                .thenReturn(new ChatDetailDto(UUID.randomUUID(), "t", List.of()));

        mvc.perform(post("/api/v1/insights/income/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Oi\"}"))
                .andExpect(status().isOk());

        verify(chats).startFromScreen(eq(account), eq("Receitas"), eq("resumo"), eq("Oi"));
    }

    @Test
    void post_mensagemVazia_retorna400() throws Exception {
        mvc.perform(post("/api/v1/insights/dashboard/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chats);
    }

    @Test
    void post_telaDesconhecida_retorna404() throws Exception {
        mvc.perform(post("/api/v1/insights/profile/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Oi\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(chats);
    }
}
