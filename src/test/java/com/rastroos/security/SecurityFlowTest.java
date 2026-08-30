package com.rastroos.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.rastroos.domain.repository.LoginAttemptRepository;
import com.rastroos.domain.repository.UserRepository;

import org.springframework.security.web.FilterChainProxy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityFlowTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private UserRepository users;
    @Autowired private LoginAttemptRepository attempts;
    @Autowired private LockoutChecker lockoutChecker;
    @Autowired private BruteForceFilter bruteForceFilter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        bruteForceFilter.resetForTests();
    }

    // ── rotas públicas ───────────────────────────────────────────────────────
    @Test
    void rotasPublicasNaoExigemAuth() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());          // login mora na landing
        mvc.perform(get("/auth/signup")).andExpect(status().isOk());
        mvc.perform(get("/auth/forgot")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rotaPrivadaSemAuthRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/app/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**openLogin=login"));   // landing c/ drawer de login
    }

    // ── headers de segurança ─────────────────────────────────────────────────
    @Test
    void headersDeSegurancaAplicados() throws Exception {
        mvc.perform(get("/"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Referrer-Policy"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    // ── CSRF ─────────────────────────────────────────────────────────────────
    @Test
    void postSemCsrfEhBloqueado() throws Exception {
        mvc.perform(post("/auth/login")
                        .param("username", "admin@rastroos.local")
                        .param("password", "ChangeMe!2026"))
                .andExpect(status().isForbidden());
    }

    // ── login feliz (admin seed) ─────────────────────────────────────────────
    @Test
    void loginAdminDoSeedRedirecionaParaChangePassword() throws Exception {
        mvc.perform(post("/auth/login").with(csrf())
                        .param("username", "admin@rastroos.local")
                        .param("password", "ChangeMe!2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/profile/change-password"));
    }

    // ── login falho ──────────────────────────────────────────────────────────
    @Test
    void loginComSenhaErradaRedirecionaParaLoginComError() throws Exception {
        mvc.perform(post("/auth/login").with(csrf())
                        .param("username", "admin@rastroos.local")
                        .param("password", "WrongPass!2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**error=invalid**"));
    }

    // ── lockout após 5 falhas ────────────────────────────────────────────────
    @Test
    void aposMaxFalhasUsuarioEntraEmLockout() throws Exception {
        String victim = "victim@rastroos.local";
        for (int i = 0; i < lockoutChecker.getMaxFailures(); i++) {
            mvc.perform(post("/auth/login").with(csrf())
                            .param("username", victim)
                            .param("password", "wrong-" + i))
                    .andExpect(status().is3xxRedirection());
        }
        // próximo POST deve bater no LockoutPreAuthFilter
        mvc.perform(post("/auth/login").with(csrf())
                        .param("username", victim)
                        .param("password", "still-wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**error=locked**"));
    }
}
