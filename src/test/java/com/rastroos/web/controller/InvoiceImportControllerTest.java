package com.rastroos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.rastroos.domain.exception.InvoiceReadException;
import com.rastroos.domain.service.InvoiceImportService;
import com.rastroos.domain.service.InvoiceLineType;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.CategoryOptionDto;
import com.rastroos.web.dto.InvoiceImportResult;
import com.rastroos.web.dto.InvoiceReviewItem;
import com.rastroos.web.dto.InvoiceReviewView;
import com.rastroos.web.form.InvoiceImportForm;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.support.PeriodResolver;

@WebMvcTest(controllers = InvoiceImportController.class,
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
@Import({InvoiceImportControllerTest.Config.class, PeriodResolver.class})
class InvoiceImportControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private InvoiceImportService service;
    @MockitoBean private CurrentUser currentUser;

    private final UUID userId = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();

    @TestConfiguration
    static class Config {
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @BeforeEach
    void setUp() {
        when(currentUser.requireEffectiveId()).thenReturn(userId);
    }

    @Test
    void extract_renderizaAConferenciaComTodosOsCasos() throws Exception {
        when(service.read(eq(userId), eq(cardId), any(), eq(YearMonth.of(2026, 9)))).thenReturn(review());

        String html = mvc.perform(multipart("/app/cards/{id}/invoice/extract", cardId)
                        .file(pdf()).param("ym", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/invoice-review"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("data-modal-content")
                .contains("data-invoice-review")
                .contains("/app/cards/" + cardId + "/invoice/import")
                .contains("name=\"items[0].description\"")
                .contains("name=\"items[0].installment\" value=\"3\"")
                .contains("name=\"items[4].type\" value=\"PAYMENT\"")
                .contains("Lança também <span>7</span> parcela(s) nas próximas faturas")
                .contains("Já está no sistema: não será lançado de novo.")
                .contains("Possível duplicado")
                .contains("Parece com “<span>Almoço</span>”")
                .contains("Ajustar valor")
                .contains("Esta fatura é do cartão final <strong>5678</strong>")
                .contains("crédito(s) e pagamento(s) não importados")
                .contains("data-cents=\"10000\"");
        // Já lançado não tem caixa de seleção: não há como mandar lançar de novo.
        assertThat(html).doesNotContain("name=\"items[1].selected\"");
    }

    @Test
    void extract_leituraFalhou_mostraOMotivoNoModal() throws Exception {
        when(service.read(eq(userId), eq(cardId), any(), any()))
                .thenThrow(new InvoiceReadException("account.invoice.encrypted"));
        when(service.failure(userId, cardId, "account.invoice.encrypted"))
                .thenReturn(failure("account.invoice.encrypted"));

        String html = mvc.perform(multipart("/app/cards/{id}/invoice/extract", cardId).file(pdf()))
                .andExpect(status().isOk())
                .andExpect(view().name("app/invoice-review"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("Este PDF está protegido por senha")
                .contains("Voltar ao cartão")
                .doesNotContain("data-invoice-review");
    }

    @Test
    void import_lancaERedirecionaParaOCartaoAbertoNoMesDaFatura() throws Exception {
        when(service.commit(eq(userId), eq(cardId), any()))
                .thenReturn(new InvoiceImportResult(2, 7, 0, YearMonth.of(2026, 10)));

        mvc.perform(withItems(post("/app/cards/{id}/invoice/import", cardId), 2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/cards?ym=2026-10&open=" + cardId))
                .andExpect(flash().attributeExists("importResult"));

        ArgumentCaptor<InvoiceImportForm> form = ArgumentCaptor.forClass(InvoiceImportForm.class);
        verify(service).commit(eq(userId), eq(cardId), form.capture());
        assertThat(form.getValue().getItems()).hasSize(2);
        assertThat(form.getValue().getItems().get(0).isSelected()).isTrue();
        assertThat(form.getValue().getItems().get(1).getInstallment()).isEqualTo((short) 3);
    }

    /** Uma fatura longa passa dos 256 itens que o Spring cria por padrão num bind de lista. */
    @Test
    void import_faturaLonga_naoEstouraOLimiteDoBind() throws Exception {
        when(service.commit(eq(userId), eq(cardId), any()))
                .thenReturn(new InvoiceImportResult(280, 0, 0, YearMonth.of(2026, 10)));

        mvc.perform(withItems(post("/app/cards/{id}/invoice/import", cardId), 280))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<InvoiceImportForm> form = ArgumentCaptor.forClass(InvoiceImportForm.class);
        verify(service).commit(eq(userId), eq(cardId), form.capture());
        assertThat(form.getValue().getItems()).hasSize(280);
    }

    @Test
    void import_formularioInvalido_naoGravaEAvisa() throws Exception {
        when(service.failure(userId, cardId, "account.invoice.invalid"))
                .thenReturn(failure("account.invoice.invalid"));

        mvc.perform(post("/app/cards/{id}/invoice/import", cardId)
                        .param("items[0].description", "LOJA")
                        .param("items[0].amount", "-5")
                        .param("items[0].type", "PURCHASE")
                        .param("items[0].categoryId", "outros"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/invoice-review"));

        verify(service, never()).commit(any(), any(), any());
    }

    @Test
    void review_recalculaSemGravar() throws Exception {
        when(service.review(eq(userId), eq(cardId), any())).thenReturn(review());

        mvc.perform(withItems(post("/app/cards/{id}/invoice/review", cardId), 1))
                .andExpect(status().isOk())
                .andExpect(view().name("app/invoice-review"))
                .andExpect(model().attribute("recalculated", true));

        verify(service, never()).commit(any(), any(), any());
    }

    // ── Apoio ────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder withItems(MockHttpServletRequestBuilder request, int count) {
        request.param("dueDate", "2026-10-10");
        for (int i = 0; i < count; i++) {
            request.param("items[" + i + "].description", "LOJA " + i)
                    .param("items[" + i + "].amount", "10.00")
                    .param("items[" + i + "].type", "PURCHASE")
                    .param("items[" + i + "].categoryId", "outros")
                    .param("items[" + i + "].selected", "true");
        }
        if (count > 1) {
            request.param("items[1].installment", "3").param("items[1].installments", "10");
        }
        return request;
    }

    private InvoiceReviewView review() {
        List<InvoiceReviewItem> items = List.of(
                item(0, "LOJA X", "100.00", (short) 3, (short) 10, "new", true, true, null, null, 7, 0),
                item(1, "UBER", "15.90", null, null, "existing", false, false, "UBER", "15.90", 0, 0),
                item(2, "RESTAURANTE SABOR", "45.90", null, null, "duplicate", false, true, "Almoço", "45.90", 0, 0),
                item(3, "MAGALU", "33.37", (short) 10, (short) 10, "adjust", true, true, "MAGALU", "33.33", 0, 0));
        List<InvoiceReviewItem> ignored = List.of(new InvoiceReviewItem(4, false, "20/09", "PAGAMENTO RECEBIDO",
                new BigDecimal("900.00"), null, null, InvoiceLineType.PAYMENT, "outros", "ignored", false,
                null, null, 0, 0));
        return new InvoiceReviewView(cardId, "Nubank", "#8a05be", null, "1234", null,
                LocalDate.of(2026, 10, 10), true, new BigDecimal("1095.17"), "5678", items, ignored,
                List.of(new CategoryOptionDto("outros", "Outros", "#94a3b8"),
                        new CategoryOptionDto("transporte", "Transporte", "#38bdf8")));
    }

    private static InvoiceReviewItem item(int index, String description, String amount, Short current, Short total,
                                          String status, boolean selected, boolean selectable,
                                          String matchDescription, String matchAmount,
                                          int futureToCreate, int futureExisting) {
        return new InvoiceReviewItem(index, selected, "12/09", description, new BigDecimal(amount), current, total,
                InvoiceLineType.PURCHASE, "outros", status, selectable, matchDescription,
                matchAmount == null ? null : new BigDecimal(matchAmount), futureToCreate, futureExisting);
    }

    private InvoiceReviewView failure(String key) {
        return new InvoiceReviewView(cardId, "Nubank", "#8a05be", null, "1234", key, null, false, null, null,
                List.of(), List.of(), List.of());
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "fatura.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
    }
}
