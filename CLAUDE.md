# CLAUDE.md — Regras de desenvolvimento do Rastro$

> Este arquivo orienta o Claude Code (e qualquer outro agente) ao trabalhar neste repositório. **Leia antes de qualquer alteração.** Em caso de dúvida ou conflito, parar e perguntar ao usuário — não improvisar.

---

## 1. Identidade do projeto

- **Nome**: Rastro$
- **Tipo**: Aplicação web de controle financeiro pessoal multi-usuário
- **Stack**: PostgreSQL (container) · Java 25 · Spring Boot · Thymeleaf · Spring Security · springdoc-openapi (Swagger)
- **Design de referência**: `~/Downloads/Rastro$/` (landing.html + protótipo React) — **manter fidelidade visual** na implementação Thymeleaf.
- **Documento mestre**: `Projeto.md` na raiz do repositório. Sempre consultá-lo antes de propor mudanças estruturais.

---

## 2. Regras de arquitetura (INVIOLÁVEIS)

### 2.1 MVC estrito

```
View (Thymeleaf) ⇄ Controller ⇄ Service ⇄ Repository ⇄ DB
```

- **Controller**: fino. Recebe requisição, valida (`@Valid`), delega ao Service, escolhe a view. **Não contém regra de negócio**.
- **Service**: grosso. Toda regra de negócio mora aqui. **`@Transactional` mora aqui**, nunca no Controller nem no Repository.
- **Repository**: Spring Data JPA. Sem regra de negócio. Sem `@Transactional`.
- **Entity** (`@Entity` JPA): **nunca** atravessa a camada Web (View, JSON). Sempre converter para **DTO/ViewModel/Form** via MapStruct.

### 2.2 Separação por usuário (NÃO é multi-tenant)

- O sistema **não tem tenants físicos** (sem schema/DB separado).
- A separação é **interna por usuário** via filtro em queries.
- **REGRA**: toda operação sobre recurso pessoal **DEVE** filtrar por `user_id = :currentUserId`.
- Repositórios expõem métodos como `findByIdAndUserId(...)`, `findAllByUserIdAnd...(...)`.
- Acesso a recurso de outro usuário → retornar `404 Not Found` (nunca `403`, para não vazar existência).
- **Sempre** escrever teste comprovando isolamento (usuário A não vê dados do usuário B).

### 2.3 Sem inline (HTML / CSS / JS)

| Proibido | Onde fica |
|----------|-----------|
| `<style>...</style>` ou `style="..."` em template | `src/main/resources/static/css/` |
| `<script>...</script>` (com lógica) em template | `src/main/resources/static/js/` |
| `onclick="..."`, `onchange="..."`, etc. | Listener em arquivo JS separado, anexado por `id`/`data-*` |
| HTML hardcoded em controller / service | Sempre via template Thymeleaf |

**Únicas exceções permitidas**:
- `<link rel="stylesheet" href="...">` para incluir CSS externo.
- `<script src="..." defer></script>` para incluir JS externo.
- Metas e `<title>` no `<head>`.
- Atributos Thymeleaf (`th:text`, `th:if`, `th:each`, `th:src`, `th:href`, etc.).
- `data-*` attributes (são marcação, não comportamento).

### 2.4 Arquivos separados

- Cada **tela** tem seu próprio `.html` em `templates/app/`.
- Cada **tela** tem seu próprio `.css` em `static/css/screens/`.
- Cada **tela** tem seu próprio `.js` em `static/js/screens/` (quando precisa de JS).
- Componentes reutilizáveis: `templates/fragments/` (Thymeleaf fragments).
- Modais: `templates/modals/` (um arquivo por modal).

---

## 3. Regras de segurança (PRIORIDADE MÁXIMA)

### 3.1 Sempre fazer

- ✅ Usar `BCryptPasswordEncoder` (cost 12, configurável) para senhas.
- ✅ Usar **parâmetros nomeados** (`:name`) em `@Query` JPQL.
- ✅ Habilitar **CSRF** (padrão Spring Security) — incluir token nos forms.
- ✅ Validar entrada com **Bean Validation** (`@Valid`, `@NotNull`, `@Email`, `@Size`, regex).
- ✅ Aplicar **rate limit** (Bucket4j) no `/auth/login` (5 tentativas / 15 min por IP).
- ✅ Implementar **lockout** após N falhas consecutivas para o mesmo email.
- ✅ **Hashear** códigos de verificação e tokens antes de persistir.
- ✅ Renovar **session id** ao autenticar (`HttpSession#changeSessionId` ou config equivalente).
- ✅ Cookies `Secure`, `HttpOnly`, `SameSite=Lax`.
- ✅ Headers de segurança: HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, CSP.
- ✅ CSP restrita: `default-src 'self'`. Sem `unsafe-inline` (por isso o §2.3).
- ✅ `@PreAuthorize("hasRole('ADMIN')")` em rotas admin.
- ✅ Audit log para login, logout, falhas, mudanças de senha/role, CRUD sensível.
- ✅ Respostas uniformes em login falho (não distinguir "senha errada" de "usuário inexistente").
- ✅ Validar redirect URLs (allow-list) — anti open redirect.

### 3.2 Nunca fazer

- ❌ Concatenar `String` para montar SQL/JPQL.
- ❌ Usar `EntityManager.createNativeQuery(...)` com input do usuário sem `setParameter`.
- ❌ Logar senhas, tokens, códigos de verificação crus, conteúdo monetário detalhado em prod.
- ❌ Retornar `Entity` direto em controllers REST (sempre DTO).
- ❌ Desabilitar CSRF, exceto em endpoints stateless **explicitamente** documentados.
- ❌ Usar `th:utext` com conteúdo do usuário (evita XSS).
- ❌ Subir credenciais reais no Git (usar `.env` + `application-{profile}.yml` placeholders).
- ❌ Confiar em `id` vindo do request sem validar ownership do recurso.
- ❌ Expor `actuator` completo em produção (apenas `health`, `info`, `prometheus`).
- ❌ Permitir senha fraca (< 8 chars ou sem variedade) — ver `PasswordPolicy`.
- ❌ Aceitar arquivo upload sem validar tipo, tamanho e extensão (quando houver upload).

### 3.3 Defesa em profundidade contra SQL Injection

1. **Camada 1**: ORM (JPA) sempre parametrizado.
2. **Camada 2**: Bean Validation rejeita formatos inválidos antes do service.
3. **Camada 3**: Whitelist de caracteres permitidos em campos críticos (regex).
4. **Camada 4**: PostgreSQL com usuário de aplicação **sem permissão DDL**.

### 3.4 Defesa em profundidade contra brute force

1. **Camada 1**: rate limit por IP (Bucket4j).
2. **Camada 2**: lockout por email após 5 falhas (15 min).
3. **Camada 3**: `login_attempts` registrado para análise.
4. **Camada 4**: alertas no audit log para 10+ falhas em 1h por IP.
5. **Camada 5 (futuro)**: CAPTCHA após N falhas.

---

## 4. Stack — versões e dependências

| Item | Regra |
|------|-------|
| Java | **25** (LTS). Não usar features experimentais sem aval. |
| Spring Boot | versão estável mais recente compatível com Java 25. |
| Build | Maven (padrão); Gradle aceitável se justificado. |
| Lombok | **NÃO** usar. Getters, setters, equals/hashCode, toString e construtores escritos à mão (Java nativo). Injeção de dependência **sempre via construtor** (com campos `final`). |
| MapStruct | usar para todos os mapeamentos Entity↔DTO. |
| Liquibase | toda mudança de schema → novo arquivo de changelog (`NNN-descricao.xml`) referenciado pelo `db.changelog-master.xml`. Cada `<changeSet>` tem `id` único + `author` + `<rollback>`. **Nunca editar changeSet já aplicado** (o checksum quebra). |
| Testcontainers | obrigatório para testes de repository e integração (Postgres real, não H2). |

---

## 5. Banco de dados

- **PostgreSQL 16+** rodando em **container Docker**.
- Migrations versionadas com **Liquibase** em `src/main/resources/db/changelog/` (master XML + arquivos por versão).
- Dinheiro armazenado como `BIGINT` (centavos). Conversão para `BigDecimal` só no DTO.
- Datas: `LocalDate` (datas do domínio), `Instant`/`TIMESTAMPTZ` (timestamps).
- IDs: `UUID` para entidades de domínio. `BIGSERIAL` apenas em tabelas internas (audit, login_attempts).
- Toda tabela de domínio tem `user_id` (exceto tabelas globais como `categories`).

---

## 6. Testes (obrigatório)

### 6.1 Pirâmide

- ~70% unitários (Service)
- ~25% integração leve (`@WebMvcTest`, `@DataJpaTest`)
- ~5% integração completa (`@SpringBootTest` + Testcontainers)

### 6.2 Cobertura mínima

- **80% line** no pacote `domain` (services + mappers).
- **70% branch** no pacote `domain`.
- Verificado pelo JaCoCo no `mvn verify`.

### 6.3 Testes obrigatórios por feature

- Caminho feliz.
- Validação rejeita entrada inválida.
- Isolamento por usuário (usuário B não vê dados do A).
- Autorização (não-admin bloqueado em endpoint admin).
- Persistência (`@DataJpaTest` com Testcontainers).
- Erro de domínio retorna 404/409/422 com mensagem clara.

### 6.4 Padrão de nomes

- Classes: `ClasseTest`
- Métodos: `metodo_dadoX_deveY` (snake_case_estilo) ou `should_y_when_x` (estilo BDD).
- Fixtures em `src/test/resources/fixtures/`.

---

## 7. API REST e Swagger

- Todo endpoint REST documentado com `@Operation`, `@ApiResponses`, `@Schema`.
- Caminhos versionados: `/api/v1/...`.
- Padrão de resposta de erro:
  ```json
  { "code": "INVALID_INPUT", "message": "...", "fields": { "email": "must be valid" }, "traceId": "..." }
  ```
- Swagger UI em `/swagger-ui.html` (em dev). Em prod: protegido por auth ou desabilitado.

---

## 8. Internacionalização (i18n)

- Idiomas: **PT-BR** (padrão) e **EN**.
- `messages.properties` (PT) e `messages_en.properties` (EN).
- Em templates: `th:text="#{key}"`.
- Em services: `MessageSource.getMessage("key", args, locale)`.
- Locale persistido por usuário no banco (`users.preferred_locale`).

---

## 9. Estilo de código

- Pacotes: `com.rastroos.{config|security|domain|web}`.
- Classes/Records: `PascalCase`. Métodos/variáveis: `camelCase`. Constantes: `SCREAMING_SNAKE_CASE`.
- Identificadores em **inglês**. Strings de UI em **PT/EN via i18n**.
- Imports organizados, sem wildcard.
- Linhas ≤ 120 caracteres.
- Sem comentários do tipo "// retorna a lista" — código auto-explicativo. Comentário só para **WHY** não-óbvio.

---

## 10. Commits e versionamento

- Conventional Commits sugerido: `feat: ...`, `fix: ...`, `chore: ...`, `test: ...`, `docs: ...`, `refactor: ...`, `sec: ...`.
- Commits pequenos e atômicos.
- Mensagem em inglês ou PT (consistente dentro do branch).
- **Não commitar** segredos, `target/`, `.env`, arquivos do IntelliJ (já no `.gitignore`).

---

## 11. Como o Claude Code deve agir

### 11.1 Antes de qualquer alteração

1. Ler `Projeto.md` se a tarefa for grande.
2. Ler este `CLAUDE.md` se for a primeira interação na sessão.
3. Listar arquivos relevantes antes de propor mudança.

### 11.2 Quando criar arquivo novo

1. Verificar se o arquivo já não existe.
2. Seguir a estrutura de pastas definida em `Projeto.md` §7.
3. Para template: criar HTML + CSS + JS separados desde o início.
4. Para entity nova: criar changeSet Liquibase na mesma alteração (e referenciá-lo no `db.changelog-master.xml`).

### 11.3 Quando alterar segurança

- **Sempre confirmar** com o usuário antes de relaxar qualquer controle (mesmo "só pra testar").
- Toda alteração em `SecurityConfig`, filtros, `PasswordEncoder`, headers, CSP → documentar **WHY** no commit.

### 11.4 Quando mexer com banco

- Migrations são via **Liquibase**. Toda mudança = **novo arquivo de changelog** referenciado pelo `db.changelog-master.xml`.
- Cada `<changeSet>` tem `id` único, `author` e — sempre que possível — `<rollback>`.
- **Nunca** editar changeSet já aplicado (o checksum do Liquibase quebra e o app não sobe). Para corrigir, criar um novo changeSet.
- Schema breaking? → discutir estratégia (DDL backwards-compatible em dois changelogs: adicionar coluna → deploy → migrar dados → remover coluna).
- Dados sensíveis em changelog → usar `${propriedade}` resolvido por property/ambiente, **não cru**.
- Sempre rodar `liquibase validate` (ou o equivalente Maven) antes de subir o PR.

### 11.5 Quando o desenho/UX original conflitar com regras técnicas

- Manter a **regra técnica** (especialmente segurança e §2.3).
- Adaptar a UI para chegar no mesmo resultado visual sem inline.
- Comunicar a decisão ao usuário.

### 11.6 Quando estiver bloqueado

- Parar e perguntar. Não improvisar arquitetura.
- Listar opções com trade-offs.

---

## 12. Definition of Done por tarefa

Uma tarefa só é "pronta" quando:

- [ ] Código compila (`./mvnw compile`).
- [ ] Lint/Checkstyle/SpotBugs sem novos warnings.
- [ ] Testes unitários da feature passam.
- [ ] Cobertura no novo código ≥ 80% line.
- [ ] ChangeSet Liquibase aplicado sem erro em Testcontainers (e `<rollback>` validado quando relevante).
- [ ] Endpoint REST documentado no Swagger (se aplicável).
- [ ] Template sem inline (validado via grep simples: `style=`, `<script>` com conteúdo, `onclick=`).
- [ ] Sem segredos no diff.
- [ ] Mensagem de commit clara.

---

## 13. Comandos frequentes

```bash
# subir banco
docker compose up -d postgres

# parar banco
docker compose down

# rodar app dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# rodar todos os testes
./mvnw test

# rodar testes + cobertura
./mvnw verify

# abrir relatório de cobertura
open target/site/jacoco/index.html

# OWASP Dependency Check
./mvnw org.owasp:dependency-check-maven:check
```

---

## 14. Convenções finais

- **Tudo em UTC** no banco (`TIMESTAMPTZ`). Conversão para timezone do usuário só na apresentação.
- **Idempotência** em POSTs sensíveis (criação) quando possível, via token de idempotência.
- **Paginação** default 20 itens, máximo 100. Sempre retornar `totalElements`, `totalPages`, `page`, `size`.
- **Erros de negócio**: exceções específicas (`UserNotFoundException`, `LockedAccountException`...), mapeadas no `GlobalExceptionHandler` para status HTTP corretos.

---

**Lembrete final**: este projeto trata de **dinheiro real do usuário**. Em caso de dúvida, **escolha sempre o caminho mais seguro**, mesmo que dê mais trabalho.
