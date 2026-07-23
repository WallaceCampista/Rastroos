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
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.TransactionService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.TransactionDto;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.form.TransactionForm;

@WebMvcTest(controllers = TransactionRestController.class,
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
@Import(TransactionRestControllerTest.Config.class)
class TransactionRestControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TransactionService service;
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
        TransactionDto dto = new TransactionDto(
                UUID.randomUUID(), "Mercado",
                UUID.randomUUID(), "Cartão", "#ff0000",
                "alimentacao", "Alimentação", "#00ff00",
                new BigDecimal("100.50"), LocalDate.of(2026, 5, 10),
                false, false, null, null, null);
        TransactionsPageView page = new TransactionsPageView(
                List.of(dto), 0, 20, 1L, 1,
                new BigDecimal("100.50"), BigDecimal.ZERO);
        when(service.listForMonth(eq(userId), eq(YearMonth.of(2026, 5)), any(), eq(0), eq(20)))
                .thenReturn(page);

        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Mercado"))
                .andExpect(jsonPath("$.totalAmount").value(100.5));
    }

    @Test
    void getByIdInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(userId, id))
                .thenThrow(new ResourceNotFoundException("transaction.notFound"));

        mvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void postCreateRetornaPrimeiraTransacao() throws Exception {
        UUID firstId = UUID.randomUUID();
        Transaction t = new Transaction();
        t.setId(firstId);
        when(service.create(eq(userId), any(TransactionForm.class))).thenReturn(List.of(t));

        TransactionDto dto = new TransactionDto(
                firstId, "Mercado",
                UUID.randomUUID(), "Cartão", "#ff0000",
                "alimentacao", "Alimentação", "#00ff00",
                new BigDecimal("100.50"), LocalDate.of(2026, 5, 10),
                false, false, null, null, null);
        when(service.get(userId, firstId)).thenReturn(dto);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "description", "Mercado",
                "accountId", UUID.randomUUID().toString(),
                "categoryId", "alimentacao",
                "amount", "100.50",
                "dueDate", "2026-05-10",
                "installments", 1
        ));

        mvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId.toString()))
                .andExpect(jsonPath("$.description").value("Mercado"));
    }

    @Test
    void deleteRetorna204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/transactions/{id}", id))
                .andExpect(status().isNoContent());
        verify(service).delete(userId, id);
    }

    @Test
    void togglePaidRetornaDtoAtualizado() throws Exception {
        UUID id = UUID.randomUUID();
        Transaction t = new Transaction();
        t.setId(id);
        t.setPaid(true);
        when(service.togglePaid(userId, id)).thenReturn(t);

        TransactionDto dto = new TransactionDto(
                id, "X",
                UUID.randomUUID(), "Cartão", null,
                "outros", "Outros", null,
                new BigDecimal("10.00"), LocalDate.of(2026, 5, 10),
                false, true, Instant.now(), null, null);
        when(service.get(userId, id)).thenReturn(dto);

        mvc.perform(post("/api/v1/transactions/{id}/toggle-paid", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true));
    }
}
