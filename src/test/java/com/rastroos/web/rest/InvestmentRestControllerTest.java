package com.rastroos.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
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
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentHistoryEntryDto;
import com.rastroos.web.dto.InvestmentChartData;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.dto.PortfolioSummaryDto;
import com.rastroos.web.form.InvestmentForm;
import com.rastroos.web.form.InvestmentHistoryForm;

@WebMvcTest(controllers = InvestmentRestController.class,
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
class InvestmentRestControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private InvestmentService service;
    @MockitoBean private CurrentUser currentUser;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(userId);
    }

    @Test
    void listRetornaJson() throws Exception {
        InvestmentsView view = new InvestmentsView(
                List.of(), List.of(),
                new PortfolioSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO,
                        null, BigDecimal.ZERO, new EnumMap<>(InvestmentKind.class)),
                new InvestmentChartData(List.of(), List.of(), java.util.Map.of()));
        when(service.load(userId)).thenReturn(view);

        mvc.perform(get("/api/v1/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalInvested").value(0));
    }

    @Test
    void getByIdInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(userId, id))
                .thenThrow(new ResourceNotFoundException("investment.notFound"));

        mvc.perform(get("/api/v1/investments/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void postCreateRetornaDto() throws Exception {
        UUID newId = UUID.randomUUID();
        Investment created = new Investment();
        created.setId(newId);
        when(service.create(eq(userId), any(InvestmentForm.class))).thenReturn(created);

        InvestmentDto dto = new InvestmentDto(
                newId, "Viagem", InvestmentKind.PIGGY,
                new BigDecimal("100.00"), new BigDecimal("1000.00"), 10,
                null, null, "#abc", "🐷");
        when(service.get(userId, newId)).thenReturn(dto);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Viagem",
                "kind", "PIGGY",
                "amount", "100.00",
                "goal", "1000.00"
        ));

        mvc.perform(post("/api/v1/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newId.toString()))
                .andExpect(jsonPath("$.name").value("Viagem"));
    }

    @Test
    void deleteRetorna204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/investments/{id}", id))
                .andExpect(status().isNoContent());
        verify(service).delete(userId, id);
    }

    @Test
    void historyListaPontosDoUserCorrente() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getHistory(userId, id))
                .thenReturn(List.of(
                        new InvestmentHistoryEntryDto("2026-04", new BigDecimal("100.00")),
                        new InvestmentHistoryEntryDto("2026-05", new BigDecimal("110.00"))));

        mvc.perform(get("/api/v1/investments/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].yearMonth").value("2026-04"));
    }

    @Test
    void upsertHistoryRetornaPontoSalvo() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentHistory snapshot = new InvestmentHistory();
        snapshot.setYearMonth("2026-05");
        snapshot.setAmountCents(12_345L);
        when(service.upsertHistory(eq(userId), eq(id), any(InvestmentHistoryForm.class)))
                .thenReturn(snapshot);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "yearMonth", "2026-05",
                "amount", "123.45"
        ));

        mvc.perform(post("/api/v1/investments/{id}/history", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearMonth").value("2026-05"))
                .andExpect(jsonPath("$.amount").value(123.45));
    }
}
