package com.rastroos.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.rastroos.domain.service.AiProviderSetting;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.interceptor.TopbarChipsInterceptor;

@WebMvcTest(controllers = AiSettingsRestController.class,
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
                        TopbarChipsInterceptor.class
                }))
@AutoConfigureMockMvc(addFilters = false)
class AiSettingsRestControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private AiProviderSetting setting;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private AuditLogger audit;

    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(admin);
        when(setting.known()).thenReturn(List.of("openai", "gemini"));
        when(setting.current()).thenReturn("openai");
        when(setting.locked()).thenReturn(false);
        when(setting.configured("openai")).thenReturn(true);
        when(setting.configured("gemini")).thenReturn(true);
    }

    @Test
    void getDevolveOMotorAtivoEQuaisTemChave() throws Exception {
        mvc.perform(get("/api/admin/ai/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value("openai"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.providers.openai").value(true))
                .andExpect(jsonPath("$.providers.gemini").value(true));
    }

    @Test
    void postTrocaOMotorERegistraNoAudit() throws Exception {
        when(setting.current()).thenReturn("gemini");

        mvc.perform(post("/api/admin/ai/provider")
                        .contentType("application/json")
                        .content("{\"provider\":\"gemini\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value("gemini"));

        verify(setting).set("gemini", admin);
        // Troca de motor é mudança de configuração sensível (§3.1).
        verify(audit).record(eq(admin), eq("AI_PROVIDER_CHANGED"), eq("app_settings"),
                eq(AiProviderSetting.KEY), any(), any());
    }

    @Test
    void motorRecusadoDevolve400ComOCodigoDoErro() throws Exception {
        doThrow(new IllegalArgumentException("ai.providerNotConfigured"))
                .when(setting).set(eq("gemini"), any());

        mvc.perform(post("/api/admin/ai/provider")
                        .contentType("application/json")
                        .content("{\"provider\":\"gemini\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ai.providerNotConfigured"));

        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ambienteTravadoDevolve400() throws Exception {
        doThrow(new IllegalArgumentException("ai.providerLocked"))
                .when(setting).set(any(), any());

        mvc.perform(post("/api/admin/ai/provider")
                        .contentType("application/json")
                        .content("{\"provider\":\"openai\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ai.providerLocked"));
    }

    /** Valor fora da lista fechada nem chega ao service (§3.3). */
    @Test
    void providerComFormatoInvalidoEBarradoNaValidacao() throws Exception {
        mvc.perform(post("/api/admin/ai/provider")
                        .contentType("application/json")
                        .content("{\"provider\":\"../etc/passwd\"}"))
                .andExpect(status().isBadRequest());

        verify(setting, never()).set(any(), any());
    }
}
