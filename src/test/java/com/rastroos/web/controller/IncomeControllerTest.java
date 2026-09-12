package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.IncomeService;
import com.rastroos.domain.service.IncomeSourceService;
import com.rastroos.domain.service.MonthlyFinanceAggregator;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomeSourceDto;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.form.IncomeForm;
import com.rastroos.web.support.PeriodResolver;

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
                        AuditLogger.class,
                        TopbarChipsInterceptor.class
                }))
@AutoConfigureMockMvc(addFilters = false)
@Import({IncomeControllerTest.Config.class, PeriodResolver.class})
class IncomeControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private IncomeService service;
    @MockitoBean private IncomeSourceService sources;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private MonthlyFinanceAggregator monthlyFinanceAggregator;

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
        when(sources.list(eq(userId), any(YearMonth.class))).thenReturn(List.of());
        when(sources.listActive(eq(userId), any(YearMonth.class))).thenReturn(List.of());
        // O gráfico de receitas monta 6 meses via aggregator.summarize(...).received();
        // stub all-zero evita NPE ao renderizar a tela de listagem.
        when(monthlyFinanceAggregator.summarize(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(new MonthSummaryDto("2026-05", "Mai",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, false));
    }

    @Test
    void listDeveCarregarMesAtualEmptyView() throws Exception {
        IncomesPageView empty = new IncomesPageView(
                List.of(), 0, 20, 0L, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        when(service.listForMonth(eq(userId), eq(YearMonth.of(2026, 5)), any(), eq(0), eq(20)))
                .thenReturn(empty);

        mvc.perform(get("/app/income"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income"))
                .andExpect(model().attribute("activeNav", "income"))
                .andExpect(model().attributeExists("view", "filter", "sources"));
    }

    @Test
    void newFormRenderizaComAsFontesCadastradas() throws Exception {
        mvc.perform(get("/app/income/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income-form"))
                .andExpect(model().attribute("editing", false))
                .andExpect(model().attributeExists("sourceOptions"));
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
    void createSemOrigemNemFonteFicaNaPagina() throws Exception {
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

    // ── Exclusão ─────────────────────────────────────────────

    @Test
    void deleteConfirmDeLancamentoAvulsoNaoOfereceEscopoDeSerie() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(userId, id)).thenReturn(incomeDto(id, null));

        mvc.perform(get("/app/income/{id}/delete", id))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income-delete-confirm"))
                .andExpect(model().attributeExists("del"));

        verify(sources, never()).summary(any(), any(), any());
    }

    @Test
    void deleteConfirmDeLancamentoRecorrenteCarregaAFonteEAsContagens() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(service.get(userId, id)).thenReturn(incomeDto(id, sourceId));
        when(sources.summary(eq(userId), eq(sourceId), any(YearMonth.class)))
                .thenReturn(sourceDto(sourceId));
        when(sources.countIncomes(userId, sourceId)).thenReturn(120L);
        when(sources.countIncomesFrom(userId, sourceId, YearMonth.of(2026, 5))).thenReturn(118L);

        mvc.perform(get("/app/income/{id}/delete", id))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income-delete-confirm"));

        verify(sources).countIncomes(userId, sourceId);
        verify(sources).countIncomesFrom(userId, sourceId, YearMonth.of(2026, 5));
    }

    @Test
    void deleteSemEscopoRemoveApenasOLancamento() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.require(userId, id)).thenReturn(income(id, null));

        mvc.perform(post("/app/income/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/income"))
                .andExpect(flash().attribute("ok", "income.deleted"));

        verify(service).delete(userId, id);
        verify(sources, never()).delete(any(), any(), any(), any());
    }

    /** Escopo forjado num lançamento avulso não pode virar "apagar a série". */
    @Test
    void deleteComEscopoAllEmLancamentoAvulsoAindaRemoveSoOLancamento() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.require(userId, id)).thenReturn(income(id, null));

        mvc.perform(post("/app/income/{id}/delete", id).param("scope", "ALL"))
                .andExpect(status().is3xxRedirection());

        verify(service).delete(userId, id);
        verify(sources, never()).delete(any(), any(), any(), any());
    }

    @Test
    void deleteComEscopoFromMonthEncerraASerieAPartirDoMes() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(service.require(userId, id)).thenReturn(income(id, sourceId));

        mvc.perform(post("/app/income/{id}/delete", id)
                        .param("scope", "FROM_MONTH")
                        .param("ym", "2026-06"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/income"))
                .andExpect(flash().attribute("ok", "incomeSource.closedFromMonth"));

        verify(sources).delete(userId, sourceId,
                IncomeSourceService.DeleteScope.FROM_MONTH, YearMonth.of(2026, 6));
        verify(service, never()).delete(any(), any());
    }

    @Test
    void deleteComEscopoAllEmLancamentoRecorrenteApagaASerieInteira() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(service.require(userId, id)).thenReturn(income(id, sourceId));

        mvc.perform(post("/app/income/{id}/delete", id).param("scope", "ALL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("ok", "incomeSource.deleted"));

        verify(sources).delete(eq(userId), eq(sourceId),
                eq(IncomeSourceService.DeleteScope.ALL), any(YearMonth.class));
    }

    // ── Confirmação de recebimento ───────────────────────────

    @Test
    void toggleReceivedConfirmaEVoltaParaOMes() throws Exception {
        UUID id = UUID.randomUUID();
        Income confirmado = income(id, UUID.randomUUID());
        confirmado.setReceived(true);
        when(service.toggleReceived(userId, id)).thenReturn(confirmado);

        mvc.perform(post("/app/income/{id}/toggle-received", id).param("ym", "2026-06"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/income?ym=2026-06"))
                .andExpect(flash().attribute("ok", "income.markedReceived"));

        verify(service).toggleReceived(userId, id);
    }

    @Test
    void toggleReceivedDesfazendoAvisaQueVoltouAPendente() throws Exception {
        UUID id = UUID.randomUUID();
        Income pendente = income(id, UUID.randomUUID());
        pendente.setReceived(false);
        when(service.toggleReceived(userId, id)).thenReturn(pendente);

        mvc.perform(post("/app/income/{id}/toggle-received", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("ok", "income.markedPending"));
    }

    // ── Receita fixa ─────────────────────────────────────────

    @Test
    void createSourceComDadosValidosRedirecionaComFlash() throws Exception {
        mvc.perform(post("/app/income/sources/new")
                        .param("name", "Acme Ltda")
                        .param("amount", "5000.00")
                        .param("payBusinessDay", "5")
                        .param("startMonth", "2026-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/income"))
                .andExpect(flash().attribute("ok", "incomeSource.created"));

        verify(sources).create(eq(userId), any());
    }

    @Test
    void createSourceComDiaUtilForaDoIntervaloFicaNaPagina() throws Exception {
        mvc.perform(post("/app/income/sources/new")
                        .param("name", "Acme Ltda")
                        .param("amount", "5000.00")
                        .param("payBusinessDay", "45"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/income-source-form"));

        verify(sources, never()).create(any(), any());
    }

    @Test
    void editSourceDeIdInexistenteResultaEm404() throws Exception {
        UUID id = UUID.randomUUID();
        when(sources.require(userId, id))
                .thenThrow(new ResourceNotFoundException("incomeSource.notFound"));

        mvc.perform(get("/app/income/sources/{id}/edit", id))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────

    private static IncomeDto incomeDto(UUID id, UUID sourceId) {
        return new IncomeDto(id, "Salário", sourceId, new BigDecimal("5000.00"),
                LocalDate.of(2026, 5, 5), null, null, null, null, false);
    }

    private static Income income(UUID id, UUID sourceId) {
        Income i = new Income();
        i.setId(id);
        i.setSourceId(sourceId);
        i.setSource("Salário");
        i.setAmountCents(500_000L);
        i.setIncomeDate(LocalDate.of(2026, 5, 5));
        return i;
    }

    private static IncomeSourceDto sourceDto(UUID id) {
        return new IncomeSourceDto(id, "Acme Ltda", new BigDecimal("5000.00"),
                (short) 5, null, null, 120L, UUID.randomUUID(), LocalDate.of(2026, 5, 7), false);
    }
}
