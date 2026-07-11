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

import com.rastroos.domain.service.CompareService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.MonthSummaryDto;

@WebMvcTest(controllers = CompareController.class,
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
@Import(CompareControllerTest.Config.class)
class CompareControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CompareService compare;
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
        when(currentUser.requireId()).thenReturn(userId);
    }

    @Test
    void compareCarregaComMesAtualQuandoYmAusente() throws Exception {
        when(compare.load(eq(userId), eq(YearMonth.of(2026, 5)))).thenReturn(emptyModel());

        mvc.perform(get("/app/compare"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/compare"))
                .andExpect(model().attribute("activeNav", "compare"))
                .andExpect(model().attribute("period", YearMonth.of(2026, 5)))
                .andExpect(model().attributeExists("data"))
                .andExpect(model().attributeExists("chartDataJson"));

        verify(compare).load(userId, YearMonth.of(2026, 5));
    }

    @Test
    void compareRespeitaParametroYm() throws Exception {
        YearMonth requested = YearMonth.of(2026, 1);
        when(compare.load(eq(userId), eq(requested))).thenReturn(emptyModel());

        mvc.perform(get("/app/compare").param("ym", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", requested));
    }

    @Test
    void compareComYmInvalidoFazFallbackParaMesAtual() throws Exception {
        when(compare.load(eq(userId), any())).thenReturn(emptyModel());

        mvc.perform(get("/app/compare").param("ym", "lixo"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", YearMonth.of(2026, 5)));
    }

    @Test
    void compareRenderizaTemplateComDados() throws Exception {
        // exercita as barras de taxa (th:with com le/ge), a tabela e o net negativo
        when(compare.load(eq(userId), any())).thenReturn(populatedModel());

        mvc.perform(get("/app/compare"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/compare"));
    }

    private CompareModel emptyModel() {
        return new CompareModel(List.of(), 20, null, 0, 0, null, null);
    }

    private CompareModel populatedModel() {
        // taxas cobrindo todos os tons: null, negativa, baixa (<20), alta (>=40), média
        List<MonthSummaryDto> months = List.of(
                month("2025-12", "Dez", null, "0.00"),
                month("2026-01", "Jan", -5, "-300.00"),
                month("2026-02", "Fev", 10, "500.00"),
                month("2026-03", "Mar", 45, "4500.00"),
                month("2026-04", "Abr", 25, "2500.00"),
                month("2026-05", "Mai", 22, "2200.00"));
        return new CompareModel(months, 20, 20, 3, 5, "Mar", 45);
    }

    private MonthSummaryDto month(String ym, String label, Integer rate, String net) {
        java.math.BigDecimal z = java.math.BigDecimal.ZERO;
        return new MonthSummaryDto(ym, label, z, z, z, z, z, z,
                new java.math.BigDecimal(net), z, rate, "2026-05".equals(ym));
    }
}
