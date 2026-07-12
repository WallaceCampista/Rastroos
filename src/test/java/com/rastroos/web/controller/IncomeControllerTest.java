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
import java.time.Clock;
import java.time.Instant;
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

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.service.IncomeService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.form.IncomeForm;

@WebMvcTest(controllers = IncomeController.class,
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
@Import(IncomeControllerTest.Config.class)
class IncomeControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private IncomeService service;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private CategoryRepository categories;

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
        when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
    }

    @Test
    void listDeveCarregarMesAtualEmptyView() throws Exception {
        IncomesPageView empty = new IncomesPageView(
                List.of(), 0, 20, 0L, 0, BigDecimal.ZERO);
        when(service.listForMonth(eq(userId), eq(YearMonth.of(2026, 5)), any(), eq(0), eq(20)))
                .thenReturn(empty);

        mvc.perform(get("/app/income"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income"))
                .andExpect(model().attribute("activeNav", "income"))
                .andExpect(model().attributeExists("view", "filter", "categories"));
    }

    @Test
    void newFormRenderiza() throws Exception {
        mvc.perform(get("/app/income/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income-form"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void createComDadosValidosRedirecionaComFlash() throws Exception {
        Income saved = new Income();
        saved.setId(UUID.randomUUID());
        when(service.create(eq(userId), any(IncomeForm.class))).thenReturn(saved);

        mvc.perform(post("/app/income/new")
                        .param("source", "Salário")
                        .param("amount", "3500.00")
                        .param("incomeDate", "2026-05-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/income"))
                .andExpect(flash().attribute("ok", "income.created"));

        verify(service).create(eq(userId), any(IncomeForm.class));
    }

    @Test
    void createComErroDeValidacaoFicaNaPagina() throws Exception {
        mvc.perform(post("/app/income/new")
                        .param("source", "")
                        .param("amount", "10.00")
                        .param("incomeDate", "2026-05-05"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income-form"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void editDeIdInexistenteResultaEm404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.require(userId, id))
                .thenThrow(new ResourceNotFoundException("income.notFound"));

        mvc.perform(get("/app/income/{id}/edit", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRedirecionaComFlash() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/app/income/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/income"))
                .andExpect(flash().attribute("ok", "income.deleted"));

        verify(service).delete(userId, id);
    }
}
