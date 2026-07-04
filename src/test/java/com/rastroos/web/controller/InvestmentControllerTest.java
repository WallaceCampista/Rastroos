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

import java.math.BigDecimal;
import java.util.EnumMap;
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

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.InvestmentService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.PortfolioSummaryDto;
import com.rastroos.web.form.InvestmentForm;
import com.rastroos.web.form.InvestmentHistoryForm;

@WebMvcTest(controllers = InvestmentController.class,
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
                        AuditLogger.class
                }))
@AutoConfigureMockMvc(addFilters = false)
class InvestmentControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private InvestmentService service;
    @MockitoBean private CurrentUser currentUser;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(userId);
    }

    @Test
    void listDeveCarregarComEmptyView() throws Exception {
        InvestmentsView empty = new InvestmentsView(
                List.of(), List.of(),
                new PortfolioSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO,
                        new EnumMap<>(InvestmentKind.class)));
        when(service.load(userId)).thenReturn(empty);

        mvc.perform(get("/app/investments"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/investments"))
                .andExpect(model().attribute("activeNav", "investments"))
                .andExpect(model().attributeExists("view", "kinds", "historyForm"));
    }

    @Test
    void newFormRenderiza() throws Exception {
        mvc.perform(get("/app/investments/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/investment-form"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void createComDadosValidosRedirecionaComFlash() throws Exception {
        Investment saved = new Investment();
        saved.setId(UUID.randomUUID());
        when(service.create(eq(userId), any(InvestmentForm.class))).thenReturn(saved);

        mvc.perform(post("/app/investments/new")
                        .param("name", "Viagem")
                        .param("kind", "PIGGY")
                        .param("amount", "100.00")
                        .param("goal", "1000.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/investments"))
                .andExpect(flash().attribute("ok", "investment.created"));

        verify(service).create(eq(userId), any(InvestmentForm.class));
    }

    @Test
    void createComErroDeValidacaoFicaNaPagina() throws Exception {
        mvc.perform(post("/app/investments/new")
                        .param("name", "")
                        .param("kind", "PIGGY")
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/investment-form"));
        verify(service, never()).create(any(), any());
    }

    @Test
    void editDeIdInexistenteResultaEm404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.require(userId, id))
                .thenThrow(new ResourceNotFoundException("investment.notFound"));

        mvc.perform(get("/app/investments/{id}/edit", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRedirecionaComFlash() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/app/investments/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/investments"))
                .andExpect(flash().attribute("ok", "investment.deleted"));

        verify(service).delete(userId, id);
    }

    @Test
    void addHistoryComDadosValidosChamaUpsertERedireciona() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.upsertHistory(eq(userId), eq(id), any(InvestmentHistoryForm.class)))
                .thenReturn(new com.rastroos.domain.entity.InvestmentHistory());

        mvc.perform(post("/app/investments/{id}/history", id)
                        .param("yearMonth", "2026-05")
                        .param("amount", "100.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/investments"))
                .andExpect(flash().attribute("ok", "investment.historyUpdated"));
    }

    @Test
    void addHistoryComYearMonthInvalidoSetaErrorFlash() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/app/investments/{id}/history", id)
                        .param("yearMonth", "2026/05") // formato inválido
                        .param("amount", "100.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/investments"))
                .andExpect(flash().attribute("error", "investment.historyInvalid"));
        verify(service, never()).upsertHistory(any(), any(), any());
    }
}
