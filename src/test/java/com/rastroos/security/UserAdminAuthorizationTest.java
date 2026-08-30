package com.rastroos.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Autorização das rotas de admin (Etapa 14). A regra de URL em
 * {@code SecurityConfig} barra usuários comuns em {@code /app/users/**} e
 * {@code /api/admin/**} antes de chegar ao controller.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserAdminAuthorizationTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonimoEhRedirecionadoParaLoginNaTelaDeUsuarios() throws Exception {
        mvc.perform(get("/app/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**openLogin=login"));
    }

    @Test
    @WithMockUser(username = "user@rastroos.local", roles = "USER")
    void usuarioComumRecebe403NaTelaDeUsuarios() throws Exception {
        mvc.perform(get("/app/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@rastroos.local", roles = "USER")
    void usuarioComumRecebe403NaApiAdmin() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@rastroos.local", roles = "ADMIN")
    void adminPassaPelaRegraDeUrlDaApiAdmin() throws Exception {
        // ADMIN cruza a barreira de URL; não deve receber 403/401.
        mvc.perform(get("/api/admin/users"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("ADMIN não deveria ser barrado, veio " + s);
                    }
                });
    }
}
