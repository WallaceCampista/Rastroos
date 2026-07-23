package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

import com.rastroos.domain.service.ReportService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.ReportsModel;

@WebMvcTest(controllers = ReportController.class,
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
@Import(ReportControllerTest.Config.class)
class ReportControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ReportService reports;
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
    void reportsCarregaComMesAtualQuandoYmAusente() throws Exception {
        when(reports.load(eq(userId), eq(YearMonth.of(2026, 5))))
                .thenReturn(emptyModel(YearMonth.of(2026, 5)));

        mvc.perform(get("/app/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/reports"))
                .andExpect(model().attribute("activeNav", "reports"))
                .andExpect(model().attribute("period", YearMonth.of(2026, 5)))
                .andExpect(model().attributeExists("data"))
                .andExpect(model().attributeExists("chartDataJson"));

        verify(reports).load(userId, YearMonth.of(2026, 5));
    }

    @Test
    void reportsRespeitaParametroYm() throws Exception {
        YearMonth requested = YearMonth.of(2026, 1);
        when(reports.load(eq(userId), eq(requested))).thenReturn(emptyModel(requested));

        mvc.perform(get("/app/reports").param("ym", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", requested));
    }

    @Test
    void reportsComYmInvalidoFazFallbackParaMesAtual() throws Exception {
        when(reports.load(eq(userId), any())).thenReturn(emptyModel(YearMonth.of(2026, 5)));

        mvc.perform(get("/app/reports").param("ym", "lixo"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", YearMonth.of(2026, 5)));
    }

    @Test
    void reportsRenderizaTemplateComDados() throws Exception {
        // exercita os loops th:each/th:with e o th:attr das barras
        when(reports.load(eq(userId), any())).thenReturn(populatedModel(YearMonth.of(2026, 5)));

        mvc.perform(get("/app/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/reports"));
    }

    private ReportsModel populatedModel(YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        MonthSummaryDto current = new MonthSummaryDto(ym.toString(), "Mai",
                new java.math.BigDecimal("5000.00"), new java.math.BigDecimal("3000.00"),
                new java.math.BigDecimal("1000.00"), new java.math.BigDecimal("2000.00"),
                new java.math.BigDecimal("1200.00"), new java.math.BigDecimal("1800.00"),
                new java.math.BigDecimal("2000.00"), java.math.BigDecimal.ZERO, 40, true);
        List<com.rastroos.web.dto.CategoryBreakdownDto> cats = List.of(
                new com.rastroos.web.dto.CategoryBreakdownDto("moradia", "Moradia", "#abcdef",
                        new java.math.BigDecimal("2000.00")),
                new com.rastroos.web.dto.CategoryBreakdownDto("lazer", "Lazer", "#123456",
                        new java.math.BigDecimal("1000.00")));
        List<com.rastroos.web.dto.CategoryBreakdownDto> accs = List.of(
                new com.rastroos.web.dto.CategoryBreakdownDto("acc1", "Cartão", "#ff0000",
                        new java.math.BigDecimal("3000.00")));
        List<MonthSummaryDto> trailing = List.of(current, current, current, current, current, current);
        return new ReportsModel(start, end, current, cats, accs, trailing);
    }

    private ReportsModel emptyModel(YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        MonthSummaryDto current = new MonthSummaryDto(ym.toString(), "Mai",
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null, true);
        return new ReportsModel(start, end, current, List.of(), List.of(), List.of());
    }
}
