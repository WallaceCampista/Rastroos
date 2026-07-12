package com.rastroos.observability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Etapa 17 — observabilidade. Verifica a superfície do actuator:
 * <ul>
 *   <li>health + probes (liveness/readiness) públicos;</li>
 *   <li>info público;</li>
 *   <li>prometheus exposto porém autenticado, com a tag comum {@code application};</li>
 *   <li>env NÃO exposto (restrito), mesmo autenticado → 404.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureObservability   // habilita registries de métricas reais no teste (prometheus)
@ActiveProfiles("test")
@Transactional
class ObservabilityEndpointsTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void healthEProbesSaoPublicos() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoEhPublicoEExpoeJava() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.java").exists());
    }

    @Test
    void prometheusAnonimoEhBloqueado() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "user@rastroos.local", roles = "USER")
    void prometheusAutenticadoServeMetricasComTagApplication() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("application=\"rastroos\"")));
    }

    @Test
    @WithMockUser(username = "user@rastroos.local", roles = "USER")
    void envNaoEstaExposto() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
