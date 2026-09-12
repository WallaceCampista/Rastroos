package com.rastroos.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.mockito.ArgumentCaptor;
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
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.ChatScope;
import com.rastroos.domain.service.ChatService;
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
import com.rastroos.web.interceptor.AlfredoWidgetInterceptor;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;

@WebMvcTest(controllers = ChatRestController.class,
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
@Import(ChatRestControllerTest.Config.class)
class ChatRestControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ChatService service;
    @MockitoBean private CurrentUser currentUser;

    private final UUID alice = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 9);

    @TestConfiguration
    static class Config {
        /** Relógio fixo: o escopo da conversa carrega o mês corrente. */
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    private static ChatDetailDto detail(UUID id) {
        return new ChatDetailDto(id, "Conversa", List.of(
                new ChatMessageDto(ChatMessageRole.USER, "Oi",
                        Instant.parse("2026-09-04T12:00:00Z"), false),
                new ChatMessageDto(ChatMessageRole.ASSISTANT, "Olá!",
                        Instant.parse("2026-09-04T12:00:01Z"), true)));
    }

    @Test
    void post_novaConversa_devolveAThreadCriada() throws Exception {
        UUID chatId = UUID.randomUUID();
        when(currentUser.chatScope(period)).thenReturn(ChatScope.own(alice, period));
        when(service.startAndDetail(any(ChatScope.class), eq("Oi"))).thenReturn(detail(chatId));

        mvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(chatId.toString()))
                .andExpect(jsonPath("$.messages[1].assistant").value(true));
    }

    @Test
    void post_mensagemNumaConversa_usaAContaLogada() throws Exception {
        UUID chatId = UUID.randomUUID();
        when(currentUser.chatScope(period)).thenReturn(ChatScope.own(alice, period));
        when(service.send(any(ChatScope.class), eq(chatId), eq("E aí?"))).thenReturn(detail(chatId));

        mvc.perform(post("/api/v1/chats/" + chatId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"E aí?\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatScope> scope = ArgumentCaptor.forClass(ChatScope.class);
        verify(service).send(scope.capture(), eq(chatId), eq("E aí?"));
        assertThat(scope.getValue().chatOwnerId()).isEqualTo(alice);
    }

    @Test
    void post_conversaDeOutroUsuario_retorna404() throws Exception {
        UUID chatId = UUID.randomUUID();
        when(currentUser.chatScope(period)).thenReturn(ChatScope.own(alice, period));
        when(service.send(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("chat.notFound"));

        mvc.perform(post("/api/v1/chats/" + chatId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"E aí?\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_mensagemVazia_retorna400() throws Exception {
        mvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
