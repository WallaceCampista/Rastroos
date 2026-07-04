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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.IncomeService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.form.IncomeForm;

@WebMvcTest(controllers = IncomeRestController.class,
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
@Import(IncomeRestControllerTest.Config.class)
class IncomeRestControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private IncomeService service;
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
    void getListaRetornaJson() throws Exception {
        IncomeDto dto = new IncomeDto(
                UUID.randomUUID(), "Salário",
                new BigDecimal("3500.00"),
                LocalDate.of(2026, 5, 5),
                "outros", "Outros", "#999999",
                null);
        IncomesPageView page = new IncomesPageView(
                List.of(dto), 0, 20, 1L, 1,
                new BigDecimal("3500.00"));
        when(service.listForMonth(eq(userId), eq(YearMonth.of(2026, 5)), any(), eq(0), eq(20)))
                .thenReturn(page);

        mvc.perform(get("/api/v1/incomes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].source").value("Salário"))
                .andExpect(jsonPath("$.totalAmount").value(3500.00));
    }

    @Test
    void getByIdInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(userId, id))
                .thenThrow(new ResourceNotFoundException("income.notFound"));

        mvc.perform(get("/api/v1/incomes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void postCreateRetornaDtoCriado() throws Exception {
        UUID newId = UUID.randomUUID();
        Income i = new Income();
        i.setId(newId);
        when(service.create(eq(userId), any(IncomeForm.class))).thenReturn(i);

        IncomeDto dto = new IncomeDto(
                newId, "Salário",
                new BigDecimal("3500.00"),
                LocalDate.of(2026, 5, 5),
                null, null, null, null);
        when(service.get(userId, newId)).thenReturn(dto);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "source", "Salário",
                "amount", "3500.00",
                "incomeDate", "2026-05-05"
        ));

        mvc.perform(post("/api/v1/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newId.toString()))
                .andExpect(jsonPath("$.source").value("Salário"));
    }

    @Test
    void deleteRetorna204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/incomes/{id}", id))
                .andExpect(status().isNoContent());
        verify(service).delete(userId, id);
    }
}
