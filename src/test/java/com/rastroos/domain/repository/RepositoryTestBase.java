package com.rastroos.domain.repository;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base para testes de repository:
 * <ul>
 *   <li>{@code @DataJpaTest} — slice JPA (sem contexto web), com {@code @Transactional}
 *       embutido: cada teste é rolledback ao fim, garantindo isolamento</li>
 *   <li>Postgres real do {@code docker-compose} local (perfil test → localhost:5432)</li>
 *   <li>Liquibase já aplicou os 3 changelogs no banco. O seed do admin
 *       (003-create-default-admin) usa {@code <preConditions>}, só roda se vazio.</li>
 * </ul>
 *
 * <p>Por que não Testcontainers agora? Docker Desktop 29.x retorna {@code /info}
 * vazio (regressão de docker-java vs CLI) e a stack do Testcontainers 1.21
 * ainda não converge — a config está pronta em
 * {@link com.rastroos.TestcontainersConfiguration} para reativar quando Docker
 * Desktop ou docker-java corrigir, ou em CI Linux (onde o socket padrão funciona).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public abstract class RepositoryTestBase {
}
