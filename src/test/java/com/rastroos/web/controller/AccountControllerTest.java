package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.YearMonth;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.AccountService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.form.AccountForm;
import com.rastroos.web.support.PeriodResolver;

@WebMvcTest(controllers = AccountController.class,
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
@Import({AccountControllerTest.Config.class, PeriodResolver.class})
class AccountControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private AccountService accountService;
    @MockitoBean private CurrentUser currentUser;

    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    static class Config {
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @BeforeEach
    void setUp() {
        when(currentUser.requireEffectiveId()).thenReturn(userId);
    }

    @Test
    void listDeveCarregarTresGruposNoMesAtual() throws Exception {
        AccountsView empty = new AccountsView(List.of(), List.of(), List.of(),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0);
        when(accountService.listForMonth(eq(userId), eq(YearMonth.of(2026, 5)))).thenReturn(empty);

        mvc.perform(get("/app/cards"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/cards"))
                .andExpect(model().attribute("activeNav", "cards"))
                .andExpect(model().attributeExists("view", "accountForm", "kinds"));
    }

    @Test
    void newFormDeveRenderizar() throws Exception {
        mvc.perform(get("/app/cards/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/account-form"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void createComDadosValidosSalvaERedirecionaComFlash() throws Exception {
        Account saved = new Account();
        saved.setId(UUID.randomUUID());
        saved.setUserId(userId);
        when(accountService.create(eq(userId), any(AccountForm.class))).thenReturn(saved);

        mvc.perform(post("/app/cards/new")
                        .param("name", "Inter")
                        .param("kind", "CARD")
                        .param("closeDay", "5")
                        .param("dueDay", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/cards"))
                .andExpect(flash().attribute("ok", "account.created"));

        verify(accountService).create(eq(userId), any(AccountForm.class));
    }

    @Test
    void createComErroDeValidacaoFicaNaPagina() throws Exception {
        mvc.perform(post("/app/cards/new")
                        .param("name", "")
                        .param("kind", "CARD"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/account-form"));

        verify(accountService, never()).create(any(), any());
    }

    @Test
    void editDeContaInexistenteResultaEm404() throws Exception {
        UUID id = UUID.randomUUID();
        when(accountService.require(userId, id))
                .thenThrow(new ResourceNotFoundException("account.notFound"));

        mvc.perform(get("/app/cards/{id}/edit", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteConfirmRenderizaOModalDeEscopo() throws Exception {
        UUID id = UUID.randomUUID();
        AccountSummaryDto resumo = new AccountSummaryDto(
                id, "Vivo (móvel+wifi)", AccountKind.RECURRENT, "#8b5cf6", null, null, null, null,
                new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("120.00"),
                0, 13L, "open");
        when(accountService.summary(eq(userId), eq(id), any())).thenReturn(resumo);
        when(accountService.countTransactions(userId, id)).thenReturn(13L);
        when(accountService.countTransactionsFrom(eq(userId), eq(id), any())).thenReturn(12L);

        String html = mvc.perform(get("/app/cards/{id}/delete", id).param("ym", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/account-delete-confirm"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("data-modal-content")
                .contains("Deste mês em diante")
                .contains("Apagar tudo")
                .contains("Julho/2026");
    }

    @Test
    void deleteErroDoServicoViraFlashDeErro() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalStateException("account.deleteScopeInvalid"))
                .when(accountService).delete(eq(userId), eq(id), any(), any());

        mvc.perform(post("/app/cards/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/cards"))
                .andExpect(flash().attribute("error", "account.deleteScopeInvalid"));
    }

    @Test
    void deleteSemEscopoApagaTudo() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/app/cards/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/cards"))
                .andExpect(flash().attribute("ok", "account.deleted"));

        verify(accountService).delete(eq(userId), eq(id),
                eq(AccountService.DeleteScope.ALL), any());
    }

    @Test
    void deleteFromMonthEncerraAContaEAvisa() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/app/cards/{id}/delete", id)
                        .param("scope", "FROM_MONTH")
                        .param("ym", "2026-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/cards"))
                .andExpect(flash().attribute("ok", "account.closedFromMonth"));

        verify(accountService).delete(userId, id,
                AccountService.DeleteScope.FROM_MONTH, YearMonth.of(2026, 5));
    }
}
