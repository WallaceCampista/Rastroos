package com.rastroos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real (Testcontainers) compartilhado pelos testes que precisam
 * de banco. Importar via {@code @Import(TestcontainersConfiguration.class)}.
 *
 * <p>Liquibase aplica todos os changelogs em cada subida do contexto. JPA
 * está em {@code ddl-auto: validate} — entidades batem 1:1 com o schema.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("rastroos_test")
                .withUsername("rastroos_test")
                .withPassword("rastroos_test");
    }
}
