package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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

import com.rastroos.domain.service.DashboardService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetails;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.DashboardKpisDto;
import com.rastroos.web.dto.DashboardModel;

@WebMvcTest(controllers = DashboardController.class,
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
@Import(DashboardControllerTest.Config.class)
class DashboardControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private DashboardService dashboard;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private CustomUserDetails principal;

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
        when(currentUser.get()).thenReturn(Optional.of(principal));
        when(principal.getEmail()).thenReturn("alice@example.com");
        when(principal.getAuthorities()).thenReturn(List.of());
    }

    @Test
    void dashboardDeveCarregarComMesAtualQuandoYmAusente() throws Exception {
        DashboardModel data = emptyModel(YearMonth.of(2026, 5));
        when(dashboard.load(eq(userId), eq(YearMonth.of(2026, 5)))).thenReturn(data);

        mvc.perform(get("/app/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/dashboard"))
                .andExpect(model().attribute("activeNav", "dashboard"))
                .andExpect(model().attribute("period", YearMonth.of(2026, 5)))
                .andExpect(model().attributeExists("data"));

        verify(dashboard).load(userId, YearMonth.of(2026, 5));
    }

    @Test
    void dashboardRespectaParametroYm() throws Exception {
        YearMonth requested = YearMonth.of(2026, 1);
        when(dashboard.load(eq(userId), eq(requested))).thenReturn(emptyModel(requested));

        mvc.perform(get("/app/dashboard").param("ym", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", requested));
    }

    @Test
    void dashboardComYmInvalidoFazFallbackParaMesAtual() throws Exception {
        DashboardModel data = emptyModel(YearMonth.of(2026, 5));
        when(dashboard.load(eq(userId), any())).thenReturn(data);

        mvc.perform(get("/app/dashboard").param("ym", "lixo"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", YearMonth.of(2026, 5)));
    }

    private DashboardModel emptyModel(YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        DashboardKpisDto kpis = new DashboardKpisDto(
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return new DashboardModel(start, end, 0L, 0L, kpis,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
