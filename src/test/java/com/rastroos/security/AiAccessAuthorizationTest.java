package com.rastroos.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.repository.UserRepository;

/**
 * O acesso ao Alfredo é barrado no <b>servidor</b>, não só escondido na tela:
 * conta sem IA recebe 403 na tela do Alfredo e nas rotas de chat e resumo,
 * mesmo digitando a URL.
 *
 * <p>Usuários criados aqui mesmo (não os do seed), para o teste não depender do
 * estado do banco de desenvolvimento.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiAccessAuthorizationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void contaSemIaRecebe403NaTelaDoAlfredo() throws Exception {
        mvc.perform(get("/app/manager").with(user(principal(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void contaSemIaRecebe403NoChat() throws Exception {
        mvc.perform(post("/api/v1/chats")
                        .with(user(principal(false))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"oi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void contaSemIaRecebe403NosResumos() throws Exception {
        mvc.perform(get("/api/v1/insights/dashboard").with(user(principal(false))))
                .andExpect(status().isForbidden());
    }

    /** A leitura de fatura gasta tokens: sem Alfredo liberado, nem a leitura nem o lançamento passam. */
    @Test
    void contaSemIaRecebe403AoAnexarFatura() throws Exception {
        mvc.perform(multipart("/app/cards/{id}/invoice/extract", UUID.randomUUID())
                        .file(new MockMultipartFile("file", "fatura.pdf", "application/pdf", new byte[] {0x25, 0x50}))
                        .with(user(principal(false))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void contaSemIaRecebe403AoLancarFatura() throws Exception {
        mvc.perform(post("/app/cards/{id}/invoice/import", UUID.randomUUID())
                        .param("dueDate", "2026-10-10")
                        .with(user(principal(false))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void contaComIaAbreATelaDoAlfredo() throws Exception {
        mvc.perform(get("/app/manager").with(user(principal(true))))
                .andExpect(status().isOk());
    }

    /**
     * A decisão vem do banco, não do principal da sessão: retirar o acesso vale
     * na hora, sem esperar o próximo login de quem perdeu.
     */
    @Test
    void retirarOAcessoValeNaMesmaSessao() throws Exception {
        CustomUserDetails logado = principal(true);

        User noBanco = users.findById(logado.getId()).orElseThrow();
        noBanco.setAiEnabled(false);
        users.saveAndFlush(noBanco);

        mvc.perform(get("/app/manager").with(user(logado)))
                .andExpect(status().isForbidden());
    }

    /**
     * A página de erro renderiza de verdade (Thymeleaf + mensagens) e, numa
     * rota do Alfredo, explica que a IA não está liberada. O MockMvc não faz o
     * redespacho do contêiner para /error, então o teste chama /error como o
     * Tomcat chamaria.
     */
    @Test
    void paginaDeErroExplicaOBloqueioDoAlfredo() throws Exception {
        mvc.perform(get("/error")
                        .with(user(principal(false)))
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr("jakarta.servlet.error.status_code", 403)
                        .requestAttr("jakarta.servlet.error.request_uri", "/app/manager"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("O Alfredo não está liberado para a sua conta")))
                .andExpect(content().string(containsString("Código 403 em /app/manager")))
                .andExpect(content().string(containsString("/images/logo-completo-slogan.webp")))
                .andExpect(content().string(not(containsString("Whitelabel"))));
    }

    @Test
    void paginaDeErro404NaoVazaDetalheInterno() throws Exception {
        mvc.perform(get("/error")
                        .with(user(principal(true)))
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr("jakarta.servlet.error.status_code", 404)
                        .requestAttr("jakarta.servlet.error.request_uri", "/app/nao-existe")
                        .requestAttr("jakarta.servlet.error.exception",
                                new IllegalStateException("SEGREDO-INTERNO-DO-SERVIDOR")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Esta página não existe")))
                .andExpect(content().string(not(containsString("SEGREDO-INTERNO-DO-SERVIDOR"))))
                .andExpect(content().string(not(containsString("Whitelabel"))));
    }

    // ── helpers ──────────────────────────────────────────────

    private CustomUserDetails principal(boolean aiEnabled) {
        User u = new User();
        u.setName("Teste IA");
        u.setEmail("ia-" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setAiEnabled(aiEnabled);
        return new CustomUserDetails(users.saveAndFlush(u));
    }
}
