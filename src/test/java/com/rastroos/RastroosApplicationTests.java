package com.rastroos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — sobe todo o contexto Spring (web + JPA + security + liquibase)
 * contra o Postgres do docker-compose (perfil "test"). Garante que os beans
 * essenciais existem.
 */
@SpringBootTest
@ActiveProfiles("test")
class RastroosApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(RastroosApplication.class)).isNotNull();
        assertThat(context.containsBean("localeResolver")).isTrue();
        assertThat(context.containsBean("rastroosOpenAPI")).isTrue();
        assertThat(context.containsBean("securityFilterChain")).isTrue();
        assertThat(context.containsBean("passwordEncoder")).isTrue();
    }
}
