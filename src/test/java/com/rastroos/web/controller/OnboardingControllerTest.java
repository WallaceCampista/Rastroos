package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserTheme;
import com.rastroos.domain.service.AccountService;
import com.rastroos.domain.service.IncomeSourceService;
import com.rastroos.domain.service.OnboardingService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.security.PrincipalRefresher;
import com.rastroos.web.dto.OnboardingView;
import com.rastroos.web.form.AccountForm;
import com.rastroos.web.form.IncomeSourceForm;
import com.rastroos.web.form.OnboardingProfileForm;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;

@WebMvcTest(controllers = OnboardingController.class,
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
class OnboardingControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private OnboardingService onboarding;
    @MockitoBean private AccountService accounts;
    @MockitoBean private IncomeSourceService incomeSources;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private PrincipalRefresher principalRefresher;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(userId);
        when(onboarding.state(userId)).thenReturn(new OnboardingView(
                "Alice", "alice@example.com", UserTheme.dark, (short) 0, List.of(), List.of()));
    }

    @Test
    void abreNoPrimeiroPasso() throws Exception {
        mvc.perform(get("/app/onboarding"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/onboarding"))
                .andExpect(model().attribute("step", "profile"))
                .andExpect(model().attributeExists("view", "profileForm", "appearanceForm",
                        "accountForm", "incomeSourceForm"));
    }

    @Test
    void passoDesconhecidoCaiNoPrimeiro() throws Exception {
        mvc.perform(get("/app/onboarding").param("step", "../../etc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "profile"));
    }

    @Test
    void perfilValidoAvancaParaAparencia() throws Exception {
        when(onboarding.saveProfile(eq(userId), any(OnboardingProfileForm.class)))
                .thenReturn(List.of());

        mvc.perform(post("/app/onboarding/profile").param("name", "Alice Dias"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/onboarding?step=appearance"));

        verify(principalRefresher).refresh(any(), any());
    }

    @Test
    void perfilSemNomeFicaNoPasso() throws Exception {
        mvc.perform(post("/app/onboarding/profile").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("app/onboarding"))
                .andExpect(model().attribute("step", "profile"));

        verify(onboarding, never()).saveProfile(any(), any());
    }

    @Test
    void senhaAtualErradaVoltaAoPassoDoPerfil() throws Exception {
        when(onboarding.saveProfile(eq(userId), any(OnboardingProfileForm.class)))
                .thenReturn(List.of("password.currentWrong"));

        mvc.perform(post("/app/onboarding/profile")
                        .param("name", "Alice")
                        .param("currentPassword", "errada")
                        .param("newPassword", "Nova!1234")
                        .param("newPasswordConfirm", "Nova!1234"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "profile"));

        verify(principalRefresher, never()).refresh(any(), any());
    }

    @Test
    void aparenciaSalvaEAvancaParaCartoes() throws Exception {
        mvc.perform(post("/app/onboarding/appearance")
                        .param("theme", "light")
                        .param("paletteIndex", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/onboarding?step=cards"));

        verify(onboarding).saveAppearance(userId, UserTheme.light, (short) 7);
    }

    @Test
    void paletaForaDoIntervaloFicaNoPasso() throws Exception {
        mvc.perform(post("/app/onboarding/appearance")
                        .param("theme", "dark")
                        .param("paletteIndex", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "appearance"));

        verify(onboarding, never()).saveAppearance(any(), any(), org.mockito.ArgumentMatchers.anyShort());
    }

    @Test
    void cartaoValidoEhCriadoEVoltaParaOMesmoPasso() throws Exception {
        mvc.perform(post("/app/onboarding/cards")
                        .param("kind", "DEBIT")
                        .param("name", "Débito BB")
                        .param("colorHex", "#6366f1")
                        .param("last4", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/onboarding?step=cards"));

        verify(accounts).create(eq(userId), any(AccountForm.class));
    }

    /** O passo 3 é só de cartões: um BILL vindo de um POST forjado é recusado. */
    @Test
    void tipoDeContaQueNaoEhCartaoEhRecusadoNoWizard() throws Exception {
        mvc.perform(post("/app/onboarding/cards")
                        .param("kind", "BILL")
                        .param("name", "Aluguel")
                        .param("colorHex", "#6366f1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "cards"));

        verify(accounts, never()).create(any(), any());
    }

    @Test
    void receitaFixaValidaEhCriadaEVoltaParaOMesmoPasso() throws Exception {
        mvc.perform(post("/app/onboarding/income")
                        .param("name", "Acme Ltda")
                        .param("amount", "5000.00")
                        .param("payBusinessDay", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/onboarding?step=income"));

        verify(incomeSources).create(eq(userId), any(IncomeSourceForm.class));
    }

    @Test
    void nomeDeReceitaFixaRepetidoFicaNoPasso() throws Exception {
        when(incomeSources.create(eq(userId), any(IncomeSourceForm.class)))
                .thenThrow(new IllegalArgumentException("incomeSource.duplicateName"));

        mvc.perform(post("/app/onboarding/income")
                        .param("name", "Acme Ltda")
                        .param("amount", "5000.00")
                        .param("payBusinessDay", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "income"));
    }

    @Test
    void concluirMarcaOWizardEVaiParaODashboard() throws Exception {
        mvc.perform(post("/app/onboarding/finish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/dashboard"));

        verify(onboarding).complete(userId);
    }

    /** "Pular" também encerra de vez: o wizard não volta a aparecer. */
    @Test
    void pularTambemMarcaOWizardComoVencido() throws Exception {
        mvc.perform(post("/app/onboarding/skip"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/dashboard"));

        verify(onboarding).complete(userId);
    }

    @Test
    void cartaoDeCreditoAceitaFechamentoEVencimento() throws Exception {
        mvc.perform(post("/app/onboarding/cards")
                        .param("kind", AccountKind.CARD.name())
                        .param("name", "Nubank")
                        .param("colorHex", "#6366f1")
                        .param("closeDay", "5")
                        .param("dueDay", "12"))
                .andExpect(status().is3xxRedirection());

        verify(accounts).create(eq(userId), any(AccountForm.class));
    }
}
