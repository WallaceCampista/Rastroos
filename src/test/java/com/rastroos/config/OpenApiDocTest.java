package com.rastroos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica o documento OpenAPI gerado pelo springdoc: acessível sem auth
 * (rota pública), com o esquema de segurança de sessão declarado e os
 * endpoints REST publicados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocTest {

    @Autowired private MockMvc mvc;

    @Test
    void apiDocsExpoeInfoEEsquemaDeSegurancaDeSessao() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Rastro$ API"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"))
                .andExpect(jsonPath("$.security[0].sessionCookie").exists());
    }

    @Test
    void apiDocsPublicaEndpointsRestEDocumentados() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/incomes'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transactions'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/investments'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/reset-password'].post").exists());
    }

    @Test
    void swaggerUiEstaAcessivelSemAuth() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
