package com.rastroos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Hardening final (Etapa 16). Trava, por teste, os controles de segurança que
 * um "pen-test interno" verificaria de fora:
 * <ul>
 *   <li>CSP estrita (sem {@code unsafe-inline}/{@code unsafe-eval}) — mitiga XSS;</li>
 *   <li>headers de segurança presentes (nosniff, frame DENY, HSTS, referrer, permissions);</li>
 *   <li>CSRF obrigatório em escritas (form e API);</li>
 *   <li>fronteira de acesso: anônimo não alcança recurso autenticado.</li>
 * </ul>
 * Isolamento por usuário (IDOR) é coberto nos testes de service de cada domínio
 * (acesso cruzado → 404).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityHardeningTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void cspEhEstritaSemUnsafeInlineNemUnsafeEval() throws Exception {
        MvcResult res = mvc.perform(get("/auth/login")).andExpect(status().isOk()).andReturn();
        String csp = res.getResponse().getHeader("Content-Security-Policy");

        assertThat(csp).isNotBlank();
        assertThat(csp).contains("default-src 'self'");
        assertThat(csp).contains("script-src 'self'");
        assertThat(csp).contains("style-src 'self'");
        assertThat(csp).contains("object-src 'none'");
        assertThat(csp).contains("frame-ancestors 'none'");
        assertThat(csp).doesNotContain("unsafe-inline");
        assertThat(csp).doesNotContain("unsafe-eval");
    }

    @Test
    void headersDeSegurancaPresentes() throws Exception {
        // secure(true) simula HTTPS — necessário para o header HSTS ser emitido
        // (o Spring Security só envia Strict-Transport-Security em conexões seguras).
        mvc.perform(get("/auth/login").secure(true))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists("Referrer-Policy"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @WithMockUser(username = "user@rastroos.local", roles = "USER")
    void escritaNaApiSemCsrfEhBloqueada() throws Exception {
        mvc.perform(post("/api/v1/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"x\",\"amount\":\"1.00\",\"incomeDate\":\"2026-05-05\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@rastroos.local", roles = "USER")
    void escritaNoFormSemCsrfEhBloqueada() throws Exception {
        mvc.perform(post("/app/expenses/new").param("description", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonimoNaoAlcancaRecursoAutenticado() throws Exception {
        int apiStatus = mvc.perform(get("/api/v1/incomes")).andReturn().getResponse().getStatus();
        assertThat(apiStatus).isNotEqualTo(200);

        mvc.perform(get("/app/dashboard"))
                .andExpect(status().is3xxRedirection());
    }
}
