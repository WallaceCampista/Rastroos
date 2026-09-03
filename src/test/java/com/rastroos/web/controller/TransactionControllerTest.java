package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.exception.InvalidUploadException;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.service.ExpenseExtractionService;
import com.rastroos.domain.service.ExpenseExtractionSource;
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
import com.rastroos.web.dto.ExtractedExpense;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.form.TransactionForm;

@WebMvcTest(controllers = TransactionController.class,
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
@Import(TransactionControllerTest.Config.class)
class TransactionControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private TransactionService service;
    @MockitoBean private ExpenseExtractionService extraction;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private AccountRepository accounts;
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
        when(accounts.findAllByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
    }

    @Test
    void listDeveRenderizarComMesAtualSemFiltros() throws Exception {
        TransactionsPageView empty = new TransactionsPageView(
                List.of(), 0, 20, 0L, 0,
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(service.listForMonth(eq(userId), eq(YearMonth.of(2026, 5)), any(), eq(0), eq(20)))
                .thenReturn(empty);

        mvc.perform(get("/app/expenses"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/expenses"))
                .andExpect(model().attribute("activeNav", "expenses"))
                .andExpect(model().attributeExists("view", "filter", "accounts", "categories"));
    }

    @Test
    void newFormRenderiza() throws Exception {
        mvc.perform(get("/app/expenses/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/transaction-form"))
                .andExpect(model().attribute("editing", false))
                .andExpect(model().attributeExists("accountOptions", "categoryOptions"));
    }

    @Test
    void extractRetornaFormularioPreenchidoParaValidacao() throws Exception {
        ExtractedExpense ex = new ExtractedExpense("Compra no cartão", null,
                LocalDate.of(2026, 5, 15), false, null, null, null, true);
        when(extraction.extract(eq(userId), any(), eq(ExpenseExtractionSource.RECEIPT))).thenReturn(ex);

        MockMultipartFile file = new MockMultipartFile(
                "file", "nota.jpg", "image/jpeg", new byte[] {1, 2, 3});

        mvc.perform(multipart("/app/expenses/extract").file(file).param("source", "receipt"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/transaction-form"))
                .andExpect(model().attribute("extracted", true))
                .andExpect(model().attribute("extractDemo", true))
                .andExpect(model().attributeExists("transactionForm", "accountOptions", "categoryOptions"));
    }

    @Test
    void extractComArquivoInvalidoMostraErroNoFormulario() throws Exception {
        when(extraction.extract(eq(userId), any(), any()))
                .thenThrow(new InvalidUploadException("transaction.extract.badType"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", "oi".getBytes());

        mvc.perform(multipart("/app/expenses/extract").file(file).param("source", "document"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/transaction-form"))
                .andExpect(model().attributeExists("extractError"))
                .andExpect(model().attributeDoesNotExist("extracted"));
    }

    @Test
    void createComDadosValidosRedirecionaComFlash() throws Exception {
        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        when(service.create(eq(userId), any(TransactionForm.class))).thenReturn(List.of(saved));

        mvc.perform(post("/app/expenses/new")
                        .param("description", "Mercado")
                        .param("accountId", UUID.randomUUID().toString())
                        .param("categoryId", "outros")
                        .param("amount", "100.50")
                        .param("dueDate", "2026-05-12")
                        .param("installments", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/expenses"))
                .andExpect(flash().attribute("ok", "transaction.created"));

        verify(service).create(eq(userId), any(TransactionForm.class));
    }

    @Test
    void createComParcelasUsaFlashDeInstallments() throws Exception {
        Transaction a = new Transaction(); a.setId(UUID.randomUUID());
        Transaction b = new Transaction(); b.setId(UUID.randomUUID());
        Transaction c = new Transaction(); c.setId(UUID.randomUUID());
        when(service.create(eq(userId), any(TransactionForm.class)))
                .thenReturn(List.of(a, b, c));

        mvc.perform(post("/app/expenses/new")
                        .param("description", "Geladeira")
                        .param("accountId", UUID.randomUUID().toString())
                        .param("categoryId", "outros")
                        .param("amount", "300.00")
                        .param("dueDate", "2026-05-10")
                        .param("installments", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("ok", "transaction.createdInstallments"));
    }

    @Test
    void createComErroDeValidacaoFicaNaPagina() throws Exception {
        mvc.perform(post("/app/expenses/new")
                        .param("description", "")
                        .param("accountId", UUID.randomUUID().toString())
                        .param("categoryId", "outros")
                        .param("amount", "10.00")
                        .param("dueDate", "2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/transaction-form"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void editDeIdInexistenteResultaEm404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.require(userId, id))
                .thenThrow(new ResourceNotFoundException("transaction.notFound"));

        mvc.perform(get("/app/expenses/{id}/edit", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void togglePaidRedirecionaComFlash() throws Exception {
        UUID id = UUID.randomUUID();
        Transaction t = new Transaction();
        t.setId(id);
        t.setPaid(true);
        when(service.togglePaid(userId, id)).thenReturn(t);

        mvc.perform(post("/app/expenses/{id}/toggle-paid", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/expenses"))
                .andExpect(flash().attribute("ok", "transaction.markedPaid"));
    }

    @Test
    void deleteRedirecionaComFlash() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/app/expenses/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/expenses"))
                .andExpect(flash().attribute("ok", "transaction.deleted"));

        verify(service).delete(userId, id);
    }
}
