# Rastro$ — Plano de Implementação Completo

> Sistema de controle financeiro pessoal multi-usuário, desenvolvido com **PostgreSQL (container)**, **Java 25 + Spring Boot**, **Thymeleaf**, **Spring Security**, **Swagger** e cobertura completa de **testes unitários**.

---

## Sumário

1. [Visão geral do produto](#1-visão-geral-do-produto)
2. [Stack técnica](#2-stack-técnica)
3. [Arquitetura](#3-arquitetura)
4. [Modelo de dados](#4-modelo-de-dados)
5. [Estratégia de segurança](#5-estratégia-de-segurança)
6. [Etapas de implementação](#6-etapas-de-implementação)
7. [Estrutura de pastas](#7-estrutura-de-pastas)
8. [Padrões de código e qualidade](#8-padrões-de-código-e-qualidade)
9. [Estratégia de testes](#9-estratégia-de-testes)
10. [Observabilidade e operações](#10-observabilidade-e-operações)
11. [Critérios de aceite](#11-critérios-de-aceite)

---

## 1. Visão geral do produto

**Rastro$** é um sistema web para controle financeiro pessoal. O design já está pronto (em `~/Downloads/Rastro$`, com landing page + SPA React de referência) e será **reimplementado no backend Spring Boot + frontend Thymeleaf**, mantendo fidelidade visual ao mockup.

### 1.1 Telas / módulos funcionais

| # | Tela | Rota | Descrição |
|---|------|------|-----------|
| 1 | **Landing** | `/` | Página pública de marketing + drawer de login/cadastro/recuperação/verificação |
| 2 | **Login / Cadastro / Forgot / Verify** | `/auth/*` | Fluxos de autenticação com verificação por código de email + aprovação por admin |
| 3 | **Dashboard (Visão geral)** | `/app/dashboard` | KPIs (recebido, gasto, pago, a pagar, saldo), gráficos (linha de saldo, donut por categoria), lista de vencimentos próximos, resumo por conta |
| 4 | **Cartões & Contas** | `/app/cards` | Listagem de cartões e contas com saldo da fatura, dia de fechamento/vencimento, gestão CRUD |
| 5 | **Gastos Variáveis** | `/app/expenses` | Listagem, filtros, marcar como pago, editar, deletar, criar lançamento (com parcelas e recorrência) |
| 6 | **Receitas** | `/app/income` | Listagem, edição e novo lançamento de receita |
| 7 | **Investimentos** | `/app/investments` | Cofrinhos (metas) e carteira (CDB, Tesouro, LCI, limite garantido) com histórico e rendimento mensal |
| 8 | **Relatórios** | `/app/reports` | Gastos por categoria, fixo vs pontual, pago vs em aberto, evolução do saldo, peso de cada gasto |
| 9 | **Comparativo** | `/app/compare` | Comparação entre meses/anos |
| 10 | **Alfredo (Gerente IA)** | `/app/manager` | Chat com IA financeira (canvas direito, histórico esquerdo) |
| 11 | **Usuários (admin)** | `/app/users` | Tabela de usuários, KPIs, novo usuário, editar, resetar senha, desativar, excluir, ver histórico de login |
| 12 | **Suporte** | `/app/support` | Sistema de tickets (bug/feature/complaint) com status, prioridade, comentários |
| 13 | **Perfil / Trocar senha** | `/app/profile` | Editar nome, e-mail e trocar senha |

### 1.2 Funcionalidades transversais

- **Multi-idioma**: PT-BR (padrão) e EN — via Spring `MessageSource` + `LocaleResolver`.
- **Tema**: claro/escuro persistido por usuário.
- **Paletas de cor**: 18 paletas (primary + accent) persistidas por usuário.
- **Densidade visual**: compacto / normal / espaçoso.
- **Ocultar valores**: toggle global de privacidade (mascara montantes na UI).
- **Período**: seletor mês/ano por tela.

### 1.3 Separação interna (não-tenant)

O sistema **não usa multi-tenant** físico. A separação é **interna por usuário**, via filtros `WHERE user_id = :currentUserId` aplicados consistentemente em **todas as queries de domínio** — implementados em uma camada de Repository de domínio (e reforçados por testes).

---

## 2. Stack técnica

### 2.1 Backend

| Componente | Versão / Escolha | Justificativa |
|------------|------------------|---------------|
| Java | **25 LTS** | Conforme requisitado |
| Spring Boot | **última 3.x compatível com Java 25** (rotulado como "Spring Boot 25" no projeto) | Pedido do usuário; usar a release estável mais recente |
| Spring Web MVC | sim | Controllers Thymeleaf + REST |
| Spring Security | sim | Autenticação, autorização e proteções padrão |
| Spring Data JPA + Hibernate | sim | Persistência ORM + queries parametrizadas (anti SQL-injection) |
| Spring Validation (Jakarta Bean Validation) | sim | DTOs com `@Valid`, `@NotNull`, `@Email`, `@Size` |
| Thymeleaf | sim | Renderização server-side, fragments para reuso |
| springdoc-openapi (Swagger UI) | última estável | Documentação automática da REST API |
| Liquibase | sim | Migrations versionadas do schema PostgreSQL (changelogs) |
| MapStruct | sim | Mapeamento Entity ↔ DTO sem boilerplate |
| Caffeine | sim | Cache local de dados pouco voláteis (paletas, categorias) |
| Bucket4j | sim | Rate limiting (defesa de brute force) |
| Resilience4j | sim | Circuit breaker para integrações (IA) |
| HikariCP | (padrão Spring Boot) | Pool de conexões |

### 2.2 Frontend

| Componente | Detalhe |
|------------|---------|
| Thymeleaf | Templates server-side |
| HTML | **Arquivos `.html` separados em `templates/`**, sem inline |
| CSS | **Arquivos `.css` separados em `static/css/`**, sem `style="..."` inline |
| JavaScript | **Arquivos `.js` separados em `static/js/`**, sem `onclick="..."` ou `<script>` inline |
| Chart.js (ou Apache ECharts) | Gráficos (linha, donut, barras, treemap) |
| HTMX (opcional) | Atualizações parciais sem SPA |

### 2.3 Infraestrutura

| Componente | Detalhe |
|------------|---------|
| PostgreSQL | 16+ em container Docker |
| Docker Compose | orquestra db + app em dev |
| Maven | build (alternativa: Gradle) |
| JaCoCo | cobertura de testes |
| Testcontainers | testes de integração com PG real |

---

## 3. Arquitetura

### 3.1 MVC clássico (server-side)

```
┌─────────────────────────────────────────────────────────────────┐
│                       Browser (Thymeleaf)                       │
│  HTML separado · CSS separado · JS separado · sem inline        │
└──────────────┬──────────────────────────────────┬───────────────┘
               │                                  │
               ▼                                  ▼
   ┌──────────────────────┐         ┌──────────────────────────┐
   │  Web Controllers     │         │  REST Controllers        │
   │  (@Controller)       │         │  (@RestController)       │
   │  retornam View name  │         │  expostos via /api/**    │
   └──────────┬───────────┘         └──────────┬───────────────┘
              │                                │
              └────────────┬───────────────────┘
                           ▼
              ┌──────────────────────────┐
              │      Service Layer       │
              │  Regras de negócio       │
              │  Transações (@Transactional)
              │  Aplica filtro por user  │
              └──────────┬───────────────┘
                         ▼
              ┌──────────────────────────┐
              │     Repository Layer     │
              │  Spring Data JPA         │
              │  Queries parametrizadas  │
              └──────────┬───────────────┘
                         ▼
              ┌──────────────────────────┐
              │      PostgreSQL          │
              │      (container)         │
              └──────────────────────────┘
```

### 3.2 Camadas e responsabilidades

| Camada | Pacote | Responsabilidade |
|--------|--------|------------------|
| **Model (Entity)** | `domain.entity` | `@Entity` JPA; representa tabelas; **nunca exposto direto na View** |
| **DTO / Form / View Model** | `web.dto`, `web.form` | Objetos que cruzam a fronteira da camada Web |
| **Mapper** | `domain.mapper` | Conversão Entity ↔ DTO via MapStruct |
| **Repository** | `domain.repository` | Acesso a dados; **sempre recebe `userId`** quando aplicável |
| **Service** | `domain.service` | Regras de negócio; injeta `SecurityContext` para `userId` |
| **Controller (Web)** | `web.controller` | Recebe requisição HTTP, valida, chama Service, retorna view name |
| **Controller (REST)** | `web.rest` | Endpoints REST autenticados, documentados via Swagger |
| **Security** | `security` | Filtros, providers, handlers, configuração |
| **Config** | `config` | Beans (Bucket4j, Caffeine, Locale, etc.) |

### 3.3 Fluxo de uma requisição típica (ex.: criar despesa)

1. **Browser** → `POST /app/expenses` com formulário Thymeleaf
2. **CSRF token** validado pelo Spring Security
3. **Rate limit** verificado (Bucket4j)
4. **Controller** `@PostMapping` recebe `@Valid ExpenseForm`
5. **Bean Validation** rejeita dados inválidos (`BindingResult`)
6. **Service** lê `currentUserId` do `SecurityContext`
7. **Mapper** converte `ExpenseForm` → `Expense` entity (com `userId` injetado)
8. **Repository** persiste via JPA (sem concatenação de SQL)
9. **Redirect** PRG (`POST/Redirect/GET`) → `GET /app/expenses?ok=1`
10. **Log** estruturado (audit) registra a operação

---

## 4. Modelo de dados

### 4.1 Tabelas principais

```sql
-- usuário (com role e estado)
users (
  id              UUID PRIMARY KEY,
  name            VARCHAR(120) NOT NULL,
  email           VARCHAR(180) NOT NULL UNIQUE,
  email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
  phone           VARCHAR(40),
  password_hash   VARCHAR(255) NOT NULL,   -- BCrypt cost 12
  role            VARCHAR(20) NOT NULL,    -- USER | ADMIN
  status          VARCHAR(20) NOT NULL,    -- PENDING_APPROVAL | ACTIVE | DISABLED
  preferred_locale VARCHAR(5)  NOT NULL DEFAULT 'pt-BR',
  theme           VARCHAR(10)  NOT NULL DEFAULT 'dark',
  palette_index   SMALLINT     NOT NULL DEFAULT 0,
  density         VARCHAR(10)  NOT NULL DEFAULT 'regular',
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_login_at   TIMESTAMPTZ
)

-- sessões (controle de "online agora" e encerrar sessão)
user_sessions (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash   VARCHAR(255) NOT NULL,    -- nunca o token cru
  user_agent   VARCHAR(255),
  ip_address   INET,
  created_at   TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL,
  revoked_at   TIMESTAMPTZ
)

-- tentativas de login (lockout / brute force)
login_attempts (
  id          BIGSERIAL PRIMARY KEY,
  email       VARCHAR(180) NOT NULL,
  ip_address  INET,
  success     BOOLEAN NOT NULL,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- códigos de verificação (signup, reset)
verification_codes (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose     VARCHAR(20) NOT NULL,      -- EMAIL_VERIFY | PASSWORD_RESET
  code_hash   VARCHAR(255) NOT NULL,     -- nunca o código cru
  expires_at  TIMESTAMPTZ NOT NULL,
  used_at     TIMESTAMPTZ
)

-- categorias (globais; cada usuário pode customizar futuramente)
categories (
  id          VARCHAR(40) PRIMARY KEY,
  name_pt     VARCHAR(60) NOT NULL,
  name_en     VARCHAR(60) NOT NULL,
  color_hex   CHAR(7) NOT NULL,
  icon_name   VARCHAR(40) NOT NULL
)

-- contas / cartões do usuário
accounts (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name         VARCHAR(80) NOT NULL,
  kind         VARCHAR(20) NOT NULL,     -- CARD | BILL | RECURRENT
  color_hex    CHAR(7),
  icon_text    VARCHAR(8),
  close_day    SMALLINT,
  due_day      SMALLINT,
  category_id  VARCHAR(40) REFERENCES categories(id),
  is_fixed     BOOLEAN NOT NULL DEFAULT FALSE,
  closed_at    DATE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- lançamentos (despesas)
transactions (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  account_id   UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  category_id  VARCHAR(40) NOT NULL REFERENCES categories(id),
  description  VARCHAR(200) NOT NULL,
  amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
  due_date     DATE NOT NULL,
  is_fixed     BOOLEAN NOT NULL DEFAULT FALSE,
  is_paid      BOOLEAN NOT NULL DEFAULT FALSE,
  paid_at      TIMESTAMPTZ,
  installment_current SMALLINT,
  installment_total   SMALLINT,
  ends_at      DATE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- receitas
incomes (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source       VARCHAR(120) NOT NULL,
  amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
  income_date  DATE NOT NULL,
  category     VARCHAR(40),
  note         VARCHAR(200),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- investimentos
investments (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name            VARCHAR(120) NOT NULL,
  kind            VARCHAR(30) NOT NULL,  -- PIGGY | CDI | TREASURY | LIMITE_GARANTIDO | LCI | STOCK
  amount_cents    BIGINT NOT NULL,
  goal_cents      BIGINT,
  rate_label      VARCHAR(60),
  monthly_return_cents BIGINT,
  color_hex       VARCHAR(80),
  icon_text       VARCHAR(8),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- histórico mensal de cada investimento
investment_history (
  id              BIGSERIAL PRIMARY KEY,
  investment_id   UUID NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  year_month      CHAR(7) NOT NULL,         -- '2026-05'
  amount_cents    BIGINT NOT NULL
)

-- tickets de suporte
support_tickets (
  id            VARCHAR(20) PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category      VARCHAR(20) NOT NULL,        -- BUG | FEATURE | COMPLAINT
  title         VARCHAR(200) NOT NULL,
  description   TEXT NOT NULL,
  priority      VARCHAR(10) NOT NULL,        -- LOW | MEDIUM | HIGH
  status        VARCHAR(20) NOT NULL,        -- OPEN | IN_PROGRESS | DONE | CANCELED
  created_at    TIMESTAMPTZ NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL
)

support_ticket_comments (
  id            UUID PRIMARY KEY,
  ticket_id     VARCHAR(20) NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
  author_id     UUID NOT NULL REFERENCES users(id),
  author_role   VARCHAR(10) NOT NULL,        -- USER | ADMIN
  body          TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- conversas com Alfredo (gerente IA)
chats (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title       VARCHAR(120) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
)

chat_messages (
  id        BIGSERIAL PRIMARY KEY,
  chat_id   UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  role      VARCHAR(15) NOT NULL,            -- USER | ASSISTANT
  content   TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- auditoria
audit_log (
  id          BIGSERIAL PRIMARY KEY,
  user_id     UUID,
  action      VARCHAR(60) NOT NULL,
  resource    VARCHAR(60),
  resource_id VARCHAR(60),
  ip_address  INET,
  user_agent  VARCHAR(255),
  details     JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
)
```

### 4.2 Índices recomendados

```sql
CREATE INDEX ix_tx_user_date         ON transactions(user_id, due_date);
CREATE INDEX ix_tx_user_account      ON transactions(user_id, account_id);
CREATE INDEX ix_income_user_date     ON incomes(user_id, income_date);
CREATE INDEX ix_accounts_user        ON accounts(user_id);
CREATE INDEX ix_login_attempts_email ON login_attempts(email, attempted_at);
CREATE INDEX ix_sessions_user        ON user_sessions(user_id) WHERE revoked_at IS NULL;
```

### 4.3 Decisão: dinheiro como `BIGINT cents`

Evita problemas de ponto flutuante. Conversão para `BigDecimal` apenas no DTO de apresentação.

---

## 5. Estratégia de segurança

> **Princípio**: defesa em profundidade. Cada camada assume que a anterior pode falhar.

### 5.1 Autenticação

- **Login**: email + senha → BCrypt (cost 12, configurável).
- **Senhas fortes**: política de mínimo 8 caracteres, com letras maiúsculas, minúsculas, dígito e caractere especial.
- **Verificação de email**: código numérico de 6 dígitos com expiração de 10 min, **hash** no banco.
- **Aprovação de admin**: novos usuários ficam em `PENDING_APPROVAL` até liberação.
- **Trocar senha**: exige senha atual; invalida todas as outras sessões.
- **Reset de senha**: link ou código com expiração curta + uso único.

### 5.2 Sessão

- **Spring Session** com cookies `Secure`, `HttpOnly`, `SameSite=Lax`.
- **Renovação de session id** ao fazer login (`changeSessionId()`) — anti session fixation.
- **Timeout** absoluto (8h) e inativo (30 min).
- **Histórico de sessões** ativo (tabela `user_sessions`) com possibilidade de "encerrar essa sessão".

### 5.3 Brute force

- **Bucket4j**: rate limit por IP no `/auth/login` (ex.: 5 tentativas / 15 min).
- **Lockout temporário**: após 5 falhas consecutivas para mesmo email, bloqueia 15 min (consultando `login_attempts`).
- **CAPTCHA** (futuro): habilitável após N tentativas falhas.
- **Resposta uniforme**: nunca distinguir "usuário não existe" de "senha incorreta".

### 5.4 SQL injection

- **JPA + parâmetros nomeados**. Proibido `Statement.executeQuery(String)` ou concatenação em `@Query`.
- **JPQL/HQL** com `:param`, ou **Criteria API**.
- **Validação de entrada** em todos os DTOs (regex, tamanho, range).

### 5.5 XSS

- Thymeleaf por padrão escapa HTML (`th:text`). Proibido usar `th:utext` com conteúdo do usuário sem sanitização.
- **CSP (Content Security Policy)** restrita: `default-src 'self'`, `script-src 'self'`, `style-src 'self'`, `img-src 'self' data:`, `connect-src 'self'`.
- Sem inline scripts/estilos — o que reforça o CSP.

### 5.6 CSRF

- **Habilitado** por padrão no Spring Security. Token no formulário Thymeleaf via `<input type="hidden" name="${_csrf.parameterName}" ...>`.
- Endpoints REST autenticados também exigem CSRF (`X-XSRF-TOKEN`).

### 5.7 Headers de segurança

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
Content-Security-Policy: <conforme 5.5>
```

### 5.8 Autorização

- `@PreAuthorize("hasRole('ADMIN')")` nas rotas administrativas (`/app/users/**`).
- **Object-level**: toda Service que opera sobre recurso de usuário **filtra por `currentUserId`**. Acesso a recurso de outro usuário → `404 Not Found` (não `403`, para não vazar existência).

### 5.9 Logging e auditoria

- **Audit log** persistido (tabela `audit_log`) para: login, login falho, criação/edição/exclusão de recurso, troca de senha, mudança de role.
- **Nunca logar**: senhas, tokens, códigos crus, conteúdo monetário detalhado em prod.
- **Log estruturado** (JSON via Logback).

### 5.10 Dependências

- **OWASP Dependency Check** no CI.
- **Snyk** (opcional) — alertas de CVEs em dependências.
- Atualização periódica (Dependabot).

### 5.11 Configuração

- Segredos **fora do código**: `.env`, variáveis de ambiente, ou Vault.
- Profiles `dev`, `test`, `prod` — sem credenciais reais no `application-dev.yml`.

---

## 6. Etapas de implementação

> Cada etapa termina em estado **funcional, testado e commitado**.

### Etapa 0 — Bootstrap do repositório (½ dia)

- [x] `git init`
- [x] `.gitignore` (Java, Maven, IntelliJ, `.env`, `target/`)
- [x] `CLAUDE.md` com regras (arquivo gerado em paralelo)
- [x] `README.md` com instruções de setup
- [x] Estrutura de pastas base (ver §7)

### Etapa 1 — Infra Docker + PostgreSQL (½ dia)

- [x] `docker-compose.yml` com serviço `postgres:16-alpine`
  - volume nomeado, healthcheck, porta 5432 exposta apenas em dev
  - usuário/senha/dbname via `.env`
- [x] `.env.example` com placeholders
- [x] Script `scripts/db-up.sh` e `db-down.sh`
- [x] Validação: subir container, conectar via `psql`

### Etapa 2 — Esqueleto Spring Boot (1 dia)

- [x] `pom.xml` com Java 25, Spring Boot, todas as dependências (§2.1)
- [x] `application.yml` (perfis: `dev`, `test`, `prod`)
- [x] `RastroosApplication.java`
- [x] Configurações iniciais: `MessageSource`, `LocaleResolver` (PT/EN), Jackson, OpenAPI
- [x] Endpoint de healthcheck `/actuator/health`
- [x] `make run` ou `./mvnw spring-boot:run` sobe app conectado ao Postgres
- [x] **Test smoke** sobe contexto com sucesso

### Etapa 3 — Schema + Liquibase (1 dia)

- [x] `db.changelog-master.xml` apontando para os changelogs por versão
- [x] `changelog/001-initial-schema.xml` com todas as tabelas da §4.1
- [x] `changelog/002-seed-categories.xml` com categorias-base (`<loadData>` ou `<insert>`)
- [x] `changelog/003-create-default-admin.xml` (admin inicial seeded — senha pré-definida via env property, **trocar no primeiro login**)
- [x] Todo changeset com `id` único, `author`, `<rollback>` definido e (quando aplicável) `<preConditions>`
- [x] Testes: `@DataJpaTest` com Testcontainers garantindo que os changelogs rodam limpo e o `liquibase validate` passa

### Etapa 4 — Entities + Repositories (1-2 dias)

- [x] Mapeamento JPA para todas as entidades (`@Entity`, `@Table`, `@Column`) — alinhado 1:1 ao schema Liquibase
- [x] Conversões: `Money` como `long cents`, datas como `LocalDate/Instant`
- [x] `UserRepository`, `AccountRepository`, `TransactionRepository`, `IncomeRepository`, `InvestmentRepository`, `SupportTicketRepository`, `ChatRepository`, `AuditLogRepository`, `LoginAttemptRepository`, `VerificationCodeRepository`
- [x] **Métodos sempre recebem `userId`**: `findByIdAndUserId`, `findAllByUserIdAndDueDateBetween`, etc.
- [x] Testes unitários por repository (Testcontainers + `@DataJpaTest`)

### Etapa 5 — Spring Security + Autenticação (2-3 dias)

- [x] `SecurityFilterChain` config:
  - rotas públicas: `/`, `/auth/**`, `/css/**`, `/js/**`, `/images/**`, `/webjars/**`
  - rotas autenticadas: `/app/**`, `/api/**`
  - rotas admin: `/app/users/**`, `/api/admin/**`
- [x] `UserDetailsService` customizado (busca por email)
- [x] `PasswordEncoder` BCrypt(12)
- [x] `AuthenticationProvider` que valida: usuário existe, status `ACTIVE`, senha bate, não está em lockout
- [x] Filters: `BruteForceFilter` (Bucket4j), `SecurityHeadersFilter`
- [x] Handlers: `SuccessHandler` (atualiza `last_login_at`, cria `user_session`), `FailureHandler` (registra `login_attempts`)
- [x] Controllers: `/auth/login`, `/auth/logout`, `/auth/signup`, `/auth/signup/verify`, `/auth/forgot`, `/auth/forgot/confirm`
- [x] **Testes**: login feliz, login falho, lockout após 5 falhas, CSRF inválido, signup → verify → pending approval

### Etapa 6 — Templates Thymeleaf + assets (2-3 dias)

> Sem inline. **Toda regra visual em `static/css/`, todo comportamento em `static/js/`.**

- [x] Layout base `layout/base.html` com `<head>` (CSP-friendly, viewport, fonts) e `<body>` com fragments
- [x] Fragments: `fragments/header.html`, `fragments/sidebar.html`, `fragments/topbar.html`, `fragments/user-menu.html`, `fragments/footer.html`, `fragments/period-selector.html`
- [x] `static/css/`:
  - `tokens.css` (CSS vars de cor, fonte, densidade — espelha o design original)
  - `base.css` (reset, tipografia)
  - `layout.css` (sidebar, topbar, grid)
  - `components.css` (botões, cards, modais, pills)
  - `landing.css`
  - _`screens/*.css` (um por tela) — criados sob demanda nas Etapas 7+_
- [x] `static/js/`:
  - `app.js` (boot)
  - `theme.js` (dark/light, paletas)
  - `i18n.js`
  - `landing.js`
  - _`screens/*.js` (interações por tela) — criados sob demanda nas Etapas 7+_
- [x] Landing page `templates/landing.html` (réplica do design baixado, drawer login/signup/forgot com POST real + CSRF)
- [x] **Validação visual**: estrutura e classes da landing espelham o design original (`aurora`, `grain`, `hero`, `hero-mock`, `hm-card`, `hm-piggy`, seções `features/numbers/how/depoimentos/cta`); inline-styles do mockup foram convertidos em classes (`line-tight`), e forms ganharam POST real + CSRF — preservando o visual

### Etapa 7 — Dashboard + Cartões/Contas (2-3 dias)

- [x] `DashboardController` + service `DashboardService` (KPIs, séries diárias de saldo, gastos por categoria, top contas, próximos vencimentos)
- [x] `AccountController` + CRUD (criar, editar, deletar com bloqueio quando há lançamentos)
- [x] Templates: `dashboard.html`, `cards.html`, `account-form.html`
- [x] Gráficos em **canvas vanilla** (`static/js/screens/dashboard-charts.js`) — Line chart de saldo + Donut de categorias. Decisão: zero dependência externa para não sujar a CSP `script-src 'self'` nem versionar libs de terceiros. JSON inline via `<script type="application/json">` + `th:text` (sem `th:utext`).
- [x] **Testes**: services unitários (14) + controllers via MockMvc (10) + `@DataJpaTest` da query agregada com Postgres real (3) — **52/52 verdes**

### Etapa 8 — Despesas (gastos variáveis) (2 dias)

- [x] `TransactionService`: criar, editar, deletar, marcar pago/aberto, listagem com filtros (paid/fixed/account/category/search) + paginação + totais
- [x] Suporte a **parcelas** (gera N transactions com `installmentCurrent` 1..N e `dueDate` deslocada por mês)
- [x] Suporte a **recorrências** (flag `fixed=true`; projeção automática em meses futuros entrará em iteração posterior)
- [x] `TransactionController` Web (`/app/expenses`) + REST `/api/v1/transactions` documentado no Swagger (`@Operation`, `@ApiResponses`)
- [x] Templates `expenses.html` (listagem com filtros + paginação) e `transaction-form.html` (criar/editar)
- [x] **Testes**: 13 unit (parcelas, isolamento por user, toggle paid, update preserva/limpa paidAt), 8 Web MockMvc, 5 REST, 6 `@DataJpaTest` com Postgres real — **84/84 verdes**

### Etapa 9 — Receitas (1 dia)

- [x] `IncomeService` (CRUD, isolamento por user, busca por mês com filtros + paginação)
- [x] `IncomeController` Web (`/app/income`) + REST `/api/v1/incomes` documentado no Swagger
- [x] Templates `income.html` + `income-form.html` (sem inline, com filtros que auto-submit)
- [x] Testes: 8 unit + 6 Web MockMvc + 4 REST + 5 `@DataJpaTest` (Postgres real) — **107/107 verdes**

### Etapa 10 — Investimentos (2 dias)

- [x] `InvestmentService`: cofrinhos (PIGGY com meta + progresso), carteira (CDI/Tesouro/LCI/LIMITE_GARANTIDO/STOCK), histórico mensal com upsert por `(investmentId, yearMonth)`, KPIs (total investido, total metas, % alcançado, rendimento mensal estimado, agregado por tipo)
- [x] `InvestmentController` Web (`/app/investments`) + REST `/api/v1/investments` documentado no Swagger (CRUD + histórico)
- [x] Templates `investments.html` (cards de cofrinhos com barra de progresso + tabela da carteira) e `investment-form.html` (campos condicionais por tipo via JS sem inline)
- [x] Testes: 11 unit + 8 Web MockMvc + 6 REST + 5 `@DataJpaTest` (Postgres real) — **137/137 verdes**

### Etapa 11 — Relatórios + Comparativo (2 dias)

- [x] `MonthlyFinanceAggregator` compartilhado (resumo mensal: recebido/gasto/pago/a-pagar/fixo/pontual/saldo/taxa de poupança) + eixo de 6 meses reutilizável pelos dois services
- [x] `ReportService` (`/app/reports`): gastos por categoria e por conta (donut + legenda com %), pago vs a pagar, fixo vs pontual, peso de cada gasto (barras ponderadas) e linha fixo vs variável dos últimos 6 meses
- [x] `CompareService` (`/app/compare`): receita × gasto × saldo × aporte (6 meses), taxa de poupança por mês com linha-alvo + indicadores (média, meses acima da meta, melhor mês). Aporte estimado via delta do histórico de investimentos − rendimento (subquery isolada por `userId`)
- [x] `ReportController` e `CompareController` finos, com `ym` (default mês corrente). Motor de gráficos vanilla reutilizável `static/js/charts.js` (donut + multilinha) — zero dependência externa, respeita CSP `script-src 'self'`
- [x] Templates `reports.html` e `compare.html` sem inline (barras/tabelas em CSS via atributos Thymeleaf `th:attr/th:style`; JSON dos gráficos via `<script type="application/json">` + `th:text`, sem `th:utext`)
- [x] Testes: 10 unit (5 aggregator + 3 ReportService + 2 CompareService) + 8 Web MockMvc (inclui renderização real dos templates) + 2 `@DataJpaTest` com Postgres real (agregado total/pago/fixo e histórico por usuário, isolamento comprovado) — **157/157 verdes**

### Etapa 12 — Suporte (tickets) (2 dias)

- [x] `SupportService`: abrir chamado (id gerado `T-XXXXXX`, alfabeto sem ambíguos + retry anti-colisão), listar, detalhar, comentar, cancelar (dono) e trocar status (admin). Resposta do admin em chamado aberto move para `EM_ANDAMENTO`
- [x] Regras de isolamento: usuário comum só vê/age nos próprios chamados; admin vê todos. Acesso a chamado alheio (não-admin) → 404. Troca de status é admin-only (rota `@PreAuthorize("hasRole('ADMIN')")` + verificação no Service → `AccessDeniedException`)
- [x] `SupportController` (`/app/support`) fino: list (filtros status/busca + KPIs por status + paginação), form de abertura, detalhe com thread, comentar, cancelar, mudar status. Flash i18n (PT/EN)
- [x] Templates sem inline: `support.html` (tabela + KPIs + filtros), `support-form.html` (abertura) e `support-detail.html` (thread + resposta + status admin). Fragmento `fragments/support-labels.html` para rótulos PT dos enums. **Nota:** optei por páginas de formulário dedicadas (padrão consolidado das Etapas 8–11) em vez de modal, para manter consistência e evitar JS inline (§11.5)
- [x] **Bug latente corrigido:** a query `adminSearch` quebrava com título nulo (`LOWER(CONCAT('%', NULL, '%'))` → `lower(bytea)` no Postgres); trocado para o sentinela tipado `:q = ''` (padrão já usado em `IncomeRepository`), pego por teste `@DataJpaTest`
- [x] Testes: 11 unit (`SupportService`: id/status inicial, isolamento por usuário, admin×usuário, comentário em fechado, status admin-only, cancelar) + 10 Web MockMvc (inclui renderização real dos 3 templates) + 4 `@DataJpaTest` com Postgres real (contadores por usuário, `adminSearch` com filtros nulos/não-nulos, isolamento, thread) — **182/182 verdes**

### Etapa 13 — Alfredo (Gerente IA) — interface (1-2 dias)

- [x] Tela `manager.html` (`/app/manager`): histórico de conversas na lateral, tela de boas-vindas com sugestões (mini-forms POST, sem JS), thread de mensagens (bolhas usuário/assistente) e composer. `manager.js` faz auto-scroll, Enter-envia e confirmação de exclusão; `manager.css` sem inline
- [x] Persistência de conversas via `ChatService` (`chats` + `chat_messages`): abrir conversa (título derivado da 1ª mensagem), enviar mensagem (persiste par usuário/assistente com histórico como contexto), listar, apagar. Isolamento por usuário — conversa de outro usuário → 404 (mensagens acessadas só via `chat` verificado)
- [x] Integração de IA via `AlfredoAiClient` + `RestClient`: **stub por padrão** (`alfredo.base-url` vazio → resposta de demonstração, sem tráfego externo); configurando o endpoint faz a chamada real (estilo OpenAI chat-completions) com Bearer + timeouts de conexão/leitura. Chave/URL fora do código (`.env` → `AlfredoProperties`), nunca logadas
- [x] Resilience4j: circuit breaker `alfredo` (`@CircuitBreaker` com `fallbackMethod`) — falha/timeout do provedor cai numa resposta de contingência amigável; timeout via `RestClient` (connect/read). Deps adicionadas ao `pom.xml` (`resilience4j-spring-boot3` + `spring-boot-starter-aop`) e instância configurada no `application.yml`
- [x] Testes (IA mockada): 3 unit `AlfredoAiClient` (modo stub determinístico, truncagem, entradas nula/vazia) + 8 unit `ChatService` (abertura/título, par de mensagens, isolamento 404, contexto, delete) + 7 Web MockMvc (inclui renderização real do template welcome/ativo) + 3 `@DataJpaTest` (histórico desc, isolamento, ordem do thread). Smoke test confirma o contexto subindo com resilience4j/AOP — **203/203 verdes**

### Etapa 14 — Admin: gestão de usuários (2 dias)

- [x] `UserAdminService`: listar (busca+status+KPIs), detalhar (sessões ativas + histórico de login), criar, editar, trocar status, resetar senha (gera senha temporária forte, marca `passwordMustChange` e revoga sessões), excluir, encerrar sessão (uma ou todas). Salvaguardas de "tiro no pé": admin não se auto-desativa/exclui/rebaixa e não é possível remover o **último admin ativo**; email único (case-insensitive)
- [x] `UserAdminController` (`/app/users`, `@PreAuthorize("hasRole('ADMIN')")`) fino, com auditoria (`AuditLogger`) das ações sensíveis (criar/editar/status/reset/excluir/revogar sessão). **Nota:** optei por páginas dedicadas (`users.html` lista, `user-form.html` criar/editar, `user-detail.html` detalhe) em vez de modais, mantendo o padrão consolidado das Etapas 8–12 e evitando JS inline (§11.5)
- [x] Templates sem inline + `fragments/user-labels.html` (rótulos PT dos enums role/status), `screens/users.css` autocontido e `screens/users.js` (confirmação de ações destrutivas via `data-confirm`, sem `onclick`). Senha temporária exibida uma única vez via flash
- [x] Endpoints REST `/api/admin/users/**` (`UserAdminRestController`) documentados no Swagger (`@Operation`, `@ApiResponses`, `@Tag`): listar, detalhar, criar (200/409/422), atualizar, trocar status (PATCH), resetar senha, excluir (204). Só DTOs — a entidade `User` nunca cruza a fronteira Web. Nova `BusinessRuleException` → 409
- [x] **Bug latente evitado:** a query `UserRepository.search` usava `:q IS NULL` com `CONCAT` (mesmo padrão que quebrou no Postgres na Etapa 12); trocado pelo sentinela tipado `:q = ''`, coberto por `@DataJpaTest`
- [x] Testes: 18 unit (`UserAdminService`: guards de último-admin/auto-ação, email único, força da senha temporária, isolamento 404, KPIs) + 12 Web MockMvc (renderização real dos 3 templates) + 9 REST + 6 `@DataJpaTest` Postgres real (search com sentinela/status/texto, `countByRole`/`countByRoleAndStatus`, histórico de login) + 4 de autorização (`@SpringBootTest`: usuário comum → 403 em `/app/users` e `/api/admin/users`; anônimo → login; admin passa a barreira) — **249/249 verdes**

### Etapa 15 — Swagger + documentação da API (½ dia)

- [x] springdoc-openapi servindo `/swagger-ui.html` + `/v3/api-docs` (habilitado em dev/test, desligado em prod via `SPRINGDOC_ENABLED`; rotas liberadas no `SecurityConfig`)
- [x] `@Operation` + `@ApiResponses` em **todos** os endpoints dos 4 controllers REST (Incomes, Transactions, Investments, Admin·Users) e `@Tag` por controller. `@Schema` (descrição + exemplo) nos 6 forms de request e descrição nos DTOs de response desta trilha; springdoc deriva o schema dos demais records automaticamente
- [x] **Autenticação no Swagger (cookie de sessão):** `OpenApiConfig` declara um `SecurityScheme` `apiKey`/cookie sobre `JSESSIONID` + requisito global de segurança (cadeado em todas as operações); `springdoc.swagger-ui.with-credentials=true` faz o Swagger UI reaproveitar a sessão do navegador (same-origin) após login em `/auth/login`. Escritas continuam exigindo token CSRF — decisão de **não** trocar o repositório de CSRF para não relaxar a proteção (§11.3); leituras funcionam direto da UI
- [x] Testes: 3 de integração (`OpenApiDocTest`, `@SpringBootTest`) validando o `/v3/api-docs` real — doc público sem auth, esquema `sessionCookie` (apiKey/cookie/JSESSIONID) + requisito global, endpoints publicados com summary (inclui `/api/admin/users/{id}/reset-password`) e `/swagger-ui.html` acessível — **252/252 verdes**

### Etapa 16 — Hardening final de segurança (1-2 dias)

- [x] **Revisão dos headers HTTP em produção**: confirmado o conjunto no `SecurityConfig` (aplicado a todos os perfis) — HSTS (1 ano + includeSubDomains, emitido só em HTTPS), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` + `frame-ancestors 'none'`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` restrita, CSP. Perfil `prod` reforça: Swagger off (`SPRINGDOC_ENABLED`), `server.error.include-*=never`, actuator restrito a `health,info,prometheus`. Nenhuma mudança necessária além do já existente
- [x] **CSP refinada (sem `unsafe-inline`)**: descoberta e corrigida uma inconsistência latente — 25 atributos `style="..."` renderizados (via `th:style`/`th:attr`) em 7 telas **conflitavam** com a CSP `style-src 'self'` (seriam bloqueados pelo browser) e violavam o §2.3. Removidos **todos**: valores dinâmicos (cores/larguras/alturas) passam por `data-*` (`data-fill`/`data-stroke`/`data-bar-width`/`-height`/`-bottom`) aplicados por `static/js/style-bindings.js` via **CSSOM** (`element.style.*`), que **não** é governado por `style-src` — mantendo a CSP estrita intacta (§11.3: reforcei o controle em vez de relaxá-lo). Zero inline em todos os templates (grep §12 limpo)
- [x] **Verificação OWASP Top 10**: revista a matriz do Anexo A; sem `th:utext` (XSS), sem `<script>` inline com lógica, JSON via `<script type="application/json">` (bloco de dados, não executável)
- [x] **Penetration test interno (automatizado)** — `SecurityHardeningTest` (`@SpringBootTest` + cadeia real): CSP estrita **sem** `unsafe-inline`/`unsafe-eval`; headers de segurança presentes; **CSRF** obrigatório em escrita de API e de form (POST sem token → 403); fronteira de acesso (anônimo não alcança `/api/**` nem `/app/**`). IDOR coberto pelos testes de service de cada domínio (acesso cruzado → 404); SQLi por JPQL parametrizado + sentinelas testados
- [x] **OWASP Dependency Check no build**: plugin `org.owasp:dependency-check-maven` no perfil opt-in `security` (`./mvnw -Psecurity verify`), falha a build em CVE com CVSS ≥ 7 (HIGH/CRITICAL); chave do NVD via `-Dnvd.api.key` para CI. Opt-in para não baixar a base do NVD em toda build
- [x] Testes: **257/257 verdes** (5 novos em `SecurityHardeningTest`); 40 testes de renderização de template revalidados após a remoção dos estilos inline

### Etapa 17 — Observabilidade (1 dia)

- [x] **Logs estruturados (Logback JSON)**: logging estruturado nativo do Spring Boot 3.4+ (`logging.structured.format.console: logstash`) ligado no perfil `prod` (dev/test seguem legíveis). `MdcFilter` popula o MDC com `traceId` (por requisição; reaproveita/gera `X-Request-Id`, ecoado na resposta) e `userId` (quando autenticado) — ambos viram campos de topo no JSON. Registrado via `ObservabilityConfig` **depois** da cadeia do Spring Security (`DEFAULT_FILTER_ORDER + 10`) para o `SecurityContext` já estar disponível
- [x] **Métricas Prometheus em `/actuator/prometheus`**: dependência `micrometer-registry-prometheus`; endpoint exposto (base/test/prod: `health,info,prometheus`; dev amplo) e com tag comum `application=rastroos` em todas as métricas (JVM, HTTP, Hikari, …). Protegido por autenticação (não público — §3.2)
- [x] **Health, info, env (env restrito)**: `/actuator/health` + probes `liveness`/`readiness` públicos (orquestrador) — `SecurityConfig` liberado para `/actuator/health/**` (§11.3: só abre probes; demais actuator exigem auth); `/actuator/info` público expondo java/os + **build-info** (goal `build-info` do plugin → versão/artefato/timestamp); `/actuator/env` **não exposto** (restrito) mesmo autenticado → 404
- [x] Testes: 3 unit `MdcFilterTest` (gera/reaproveita traceId, ecoa header, injeta userId, limpa o MDC ao fim) + 5 `ObservabilityEndpointsTest` (`@SpringBootTest` + `@AutoConfigureObservability`): probes públicos UP, info com java, prometheus autenticado com tag `application`, prometheus anônimo bloqueado, env 404 — **265/265 verdes**

### Etapa 18 — Cobertura + qualidade (1 dia)

- [x] **JaCoCo com gate no domínio**: `check` bound ao `verify` exigindo **≥80% line + ≥70% branch** no pacote `com.rastroos.domain.service` (`element=PACKAGE`, `haltOnFailure`). Para chegar lá, cobri as maiores lacunas com testes unitários novos: `AuthServiceTest` (signup/verify/reset/change — antes 13.6%), `VerificationCodeServiceTest` (issue/consume, hash, expiração), `LoggingEmailService`, e `listForMonth`/`toDto` de `TransactionService`/`IncomeService` (switches paid/fixed, i18n pt/en, fallback conta/categoria). **Cobertura do domínio: 74.4%→91.4% line, 57.2%→76.0% branch**
- [x] **PMD / SpotBugs / Checkstyle integrados** no perfil opt-in `quality` (`./mvnw -Pquality verify` ou goals diretos), em modo relatório para servir de baseline: SpotBugs (effort Max, exclui EI_EXPOSE_REP de entities/DTOs em `config/spotbugs/exclude.xml`), PMD (errorprone+bestpractices, `config/pmd/ruleset.xml`), Checkstyle enxuto (linha ≤120, sem wildcard/imports não usados, `config/checkstyle/checkstyle.xml`)
- [x] **CI (GitHub Actions)** em `.github/workflows/ci.yml`: job `test` (Postgres 16 service + JDK 25 Temurin + `mvnw verify` = testes + gate de cobertura, artefato JaCoCo); job `static-analysis` (perfil `quality`, report-only, não bloqueia); job `dependency-check` (perfil `security`, usa `secrets.NVD_API_KEY`, não bloqueia)
- [x] Testes: +29 unit (AuthService 17, VerificationCode 6, LoggingEmail 1, Transaction/Income list 5) — **294/294 verdes**, gate de cobertura passando com folga (91.4% ≥ 80% line, 76.0% ≥ 70% branch)

### Etapa 19 — Deploy de referência (1 dia)

- [x] **`Dockerfile` multi-stage**: stage de build (JDK 25) compila o jar e monta um **JRE mínimo com `jlink`** (`java.se` + módulos jdk.* do Spring/JDBC/observabilidade); runtime **distroless non-root** (`gcr.io/distroless/java-base-debian12:nonroot`) recebe só o JRE + o jar. Cache de dependências Maven via `--mount=type=cache`, `SPRING_PROFILES_ACTIVE=prod`, `MaxRAMPercentage=75`. **Nota:** não há distroless oficial p/ Java 25 ainda → `java-base` + JRE do jlink dá o mesmo resultado (mínimo, non-root) e é portável
- [x] **`docker-compose.prod.yml`**: app + Postgres; banco **sem porta no host** (só rede interna), `depends_on` healthy, segredos por env (`${VAR:?}`), `no-new-privileges`, `read_only` + tmpfs `/tmp`, logs rotacionados. `.dockerignore` exclui `target/`, `.env`, `.git` e artefatos de agente
- [x] **README com produção**: seção "🐳 Produção (Docker)" (build da imagem, env obrigatórias, geração de hash BCrypt + APP_SECRET, endpoints de health/prometheus, logs JSON, Swagger off) + roadmap atualizado (todas as etapas ✅)
- [x] **Bug de prod pego pelo smoke test:** ao subir o container no perfil `prod`, o contexto falhava — `AuthService` exige um `EmailService`, mas `LoggingEmailService` é `@Profile({"dev","test"})` (loga o código, proibido em prod §5.9). Adicionado `UnconfiguredEmailService` (`@Profile("prod")` + `@ConditionalOnMissingBean(name="smtpEmailService")`): sobe em prod e emite **WARN sem o código**, cedendo lugar a um bean SMTP/SES real quando registrado
- [x] **Verificação e2e da imagem**: `docker build` OK (322 MB), container sobe em prod (~16s) contra o Postgres, **Liquibase aplicado**, HikariCP conectado, logs **JSON estruturados**, roda como **nonroot**; `GET /actuator/health` → `UP` (liveness/readiness 200). Suíte **295/295 verde** com o gate de cobertura passando

### Etapa 20 — Alfredo de verdade: grounding, RAG e custo sob controle (3-4 dias)

**Diagnóstico do que existia.** A IA estava desligada (sem credencial) e, pior, o chat mandava ao modelo apenas a pergunta e o histórico da conversa — **zero dados financeiros** — enquanto a saudação prometia acesso a todos os lançamentos. Ligar a chave naquele estado produziria números inventados com confiança total. Os resumos de tela eram gerados **a cada page load**, com cache só em memória e TTL de 15 min: regeravam (e cobrariam) mesmo sem nenhum dado ter mudado, e perdiam tudo a cada restart. A extração por documento/foto nunca chegou a chamar modelo nenhum.

- [x] **Dossiê de dados reais (`FinancialContextBuilder`)**: antes de cada pergunta, o servidor monta um bloco com os números exatos do dono dos dados — KPIs do mês, categorias, contas e cartões com situação, lançamentos em aberto, receitas, investimentos e 12 meses de histórico — calculados pelos mesmos services das telas, sempre filtrados por `user_id`. O prompt de sistema proíbe citar qualquer valor fora do dossiê e manda dizer "não tenho esse dado" no lugar de supor. **É o que torna a resposta conferível**; sem isso, RAG vetorial sozinho produz totais plausíveis e errados
- [x] **Busca semântica com pgvector** (changelog `012`): imagem trocada para `pgvector/pgvector:pg16`, tabela `ai_documents` com `vector(1536)` e índice HNSW cosseno. Indexa texto livre (descrição de gasto, fonte de receita, nome de conta/investimento) e é **incremental por impressão digital** — quem tem mil lançamentos e edita um paga um embedding, não mil. Entra no prompt como *pista de localização*, com instrução explícita de não somar nem contar esses itens
- [x] **Resumo de tela persistido + regeração só por mudança de dado** (changelog `013`): `ai_insights` guarda o texto por (usuário, tela, período) junto do SHA-256 dos números que o geraram. Hash igual → texto servido do banco, **sem nenhuma chamada**, não importa há quanto tempo. O TTL foi eliminado. `ai_user_state.data_version` sobe a cada escrita financeira (`UserDataChangedEvent`, entregue só **após o commit**) e um varredor com *debounce* de 20s regera em lote todas as telas afetadas em segundo plano — dez lançamentos seguidos viram uma geração, e quem passa um mês sem lançar nada não gera consumo algum
- [x] **Independência de fornecedor (`AiProvider`)**: endpoints, corpo da requisição e leitura da resposta saíram do cliente para uma interface. `AI_PROVIDER=openai|gemini` troca o motor sem tocar em código (verificado subindo o app nos dois); serviços compatíveis com o dialeto da OpenAI entram só com a URL. Checagem no boot avisa se a dimensão dos vetores do fornecedor não casar com a coluna
- [x] **Extração por visão real (`ExpenseVisionReader`)**: boleto/fatura em PDF (parte `file`) e foto de notinha (parte `image_url`) com **saída estruturada estrita** (JSON Schema). Campo ilegível volta `null` — num lançamento de dinheiro, campo vazio é melhor que campo chutado
- [x] **Custo sob controle**: livro-caixa `ai_usage` (tokens por chamada, em transação própria), teto diário por conta (`AiBudgetGuard`), rate limit por conta nas rotas que gastam IA (Bucket4j, 20/min), `max_tokens` em toda requisição, circuit breaker **por funcionalidade** (`ai-chat`, `ai-insight`, `ai-embedding`, `ai-vision`) e métricas `rastroos.ai.*`. `429 insufficient_quota` é distinguido de excesso de tráfego e **não é repetido**; falha do provedor reagenda o aquecimento com backoff em vez de tentar a cada varredura
- [x] **Chamada HTTP fora de transação**: a persistência migrou para `ChatStore`/`InsightStore`, em transações curtas. Antes, a espera de segundos pelo provedor seguraria uma conexão do pool por pergunta
- [x] **Bugs encontrados e corrigidos na verificação ao vivo**: (1) auto-invocação anulando `@Transactional` no listener de mudança de dados — o `bump` nunca rodava; (2) falha de embedding abortava o aquecimento inteiro, inclusive os resumos que funcionariam; (3) falha permanente do provedor causava tentativa a cada 30s, indefinidamente; (4) construtor extra tornou a injeção do `AiModelClient` ambígua e derrubava o contexto
- [x] **Testes**: 562 no total (111 novos), incluindo `@DataJpaTest` contra o **pgvector real** (distância de cosseno, upsert, isolamento por usuário) e `MockRestServiceServer` para retry/429/limite de dimensão. Gate de cobertura do domínio mantido

### Etapa 21 — Onboarding do primeiro acesso + receita recorrente (2 dias)

**Diagnóstico do que existia.** Quem entrava pela primeira vez caía direto num dashboard vazio, sem cartão, sem receita e com o nome que o admin digitou no cadastro. Tema e paleta já tinham coluna no banco (`users.theme`, `users.palette_index`) mas **ninguém lia nem escrevia**: a escolha vivia só no `localStorage`, então trocar de máquina perdia tudo. Receita era sempre lançamento avulso — quem recebe o mesmo salário todo mês redigitava origem, valor e categoria doze vezes por ano, e a categoria era pedida sem servir para nada (relatório de receita não usa categoria).

- [x] **Wizard de boas-vindas em 4 passos** (`/app/onboarding`, changelog `014`): abre como modal por cima de qualquer tela do app enquanto `users.onboarding_completed_at` for NULL. Perfil (nome + senha opcional) → Aparência (tema + paleta) → Cartões → Receita fixa. É **dispensável de vez**: "Pular" carimba a mesma data que "Concluir", então quem não quer configurar não é perseguido a cada login. Contas já existentes nascem marcadas no backfill — o wizard é para quem chega agora
- [x] **Cada passo é um POST-redirect-GET** que devolve o HTML do passo seguinte; `onboarding.js` troca o conteúdo do modal por fetch. A validação continua sendo do servidor, o F5 não reenvia o cartão, e **sem JS os mesmos links e forms navegam para a página cheia** do wizard. O modal fica travado (nem Esc nem clique no fundo fecham): a saída é sempre um botão, para ninguém dispensar o wizard sem saber que dispensou
- [x] **Trocar a senha continua exigindo a senha atual**, mesmo dentro do wizard. Senha é opcional ali, mas uma sessão aberta esquecida numa máquina alheia não pode virar posse permanente da conta
- [x] **Tema e paleta agora persistem no banco** (`UserPreferencesAdvice` lê do principal em sessão e alimenta o `<body>`; `PrincipalRefresher` recarrega o principal depois de salvar). A lista de 18 paletas saiu de dentro do `app.js` para `palettes.js`, compartilhada com o wizard — a **ordem do array é o contrato** de `users.palette_index`
- [x] **Cartão de débito é um tipo próprio** (`AccountKind.DEBIT`): tem os 4 últimos dígitos, não tem fatura. Modelar como `CARD` com fechamento/vencimento nulos deixaria a tela de fatura mentindo sobre um cartão que debita na hora. Aparece junto do crédito na grade de cartões
- [x] **Receita recorrente (`income_sources`)**: a empresa que paga o salário fixo. Cadastrar **materializa 10 anos de recebimentos** em `incomes` apontando para a fonte — mesma estratégia do gasto fixo permanente, e pelo mesmo motivo: dashboard, relatórios e o Alfredo somam uma tabela só, sem reinterpretar uma regra de recorrência em cada consulta. Editar a fonte reescreve só o que ainda está por vir; um aumento de salário não reescreve o holerite do ano passado
- [x] **Excluir receita com escopo, igual a excluir conta**: só este lançamento · deste mês em diante (apaga do corte para frente e **encerra** a fonte, preservando o histórico) · apagar tudo. Escopo vindo de request forjado num lançamento avulso não vira "apagar a série" — o servidor confere se há fonte antes de aceitar
- [x] **Receita não tem mais categoria**: para lançar bastam a empresa cadastrada (ou uma origem digitada) e o valor. A coluna `incomes.category` fica no banco e uma edição **não a apaga** — registro antigo continua legível para o Alfredo
- [x] **Testes**: 609 no total (47 novos) — unitários de `IncomeSourceService` (materialização, dia 31 em fevereiro, escopos de exclusão, isolamento) e `OnboardingService`, MockMvc de `OnboardingController` (os 4 passos renderizando de verdade) e `IncomeController` (escopos), e `@DataJpaTest` provando que o corte "deste mês em diante" não toca a linha de outro usuário. Gate de cobertura do domínio mantido


### Etapa 22 — Receita fixa por dia útil + confirmação de recebimento (1 dia)

**Diagnóstico do que existia.** A receita fixa guardava um dia do calendário ("dia 5") e materializava a data crua — mas salário cai no **N-ésimo dia útil**, e fim de semana ou feriado empurram a data. Pior: os 10 anos de recebimentos entravam no "total recebido" só por existirem, então o dashboard somava como dinheiro em conta um depósito programado para 2036.

- [x] **`BusinessDayCalendar`** (changelog `015`): a coluna `pay_day` virou `pay_business_day` — o nome não podia continuar mentindo sobre o que guarda. O calendário considera os feriados **bancários nacionais**: os fixos, Sexta-feira Santa, segunda e terça de Carnaval e Corpus Christi (Páscoa pelo algoritmo gregoriano anônimo); quarta-feira de cinzas **é** dia útil, e 20/11 só entra a partir de 2024 (Lei 14.759/2023). Pedir um dia útil que o mês não tem cai no último — quem recebe no 22º não deixa de receber em fevereiro
- [x] **Feriado estadual/municipal fica de fora**, por não haver como saber a cidade do usuário. É um limite declarado no formulário: se um feriado local empurrar o pagamento, a data daquele mês se corrige no próprio lançamento
- [x] **Os recebimentos já gerados foram recalculados** por um one-shot em PL/pgSQL dentro da própria migração (funções criadas, usadas e removidas), só do mês corrente em diante. A fonte da verdade do calendário continua sendo a classe Java — não sobrou lógica de feriado no banco
- [x] **`incomes.received` separa programado de recebido.** Um recebimento de receita fixa nasce pendente e só entra nos totais depois do botão **"Marcar como recebido"**, no painel de receitas fixas e na linha do mês. Lançamento avulso nasce confirmado (lançar é registrar o que já caiu) — e é assim que o backfill trata os registros antigos
- [x] **Recebido é caixa; saldo continua sendo previsão.** "Total recebido" (dashboard, relatórios, comparativo, gráfico de 6 meses) passa a somar só o confirmado, e o dashboard ganhou **"a receber"** logo abaixo. Já `net`/`savingsRate` seguem usando tudo que está lançado no mês: fossem para caixa, todo mês futuro apareceria no vermelho só porque o salário ainda não caiu, e os chips de mês e o comparativo perderiam a função de projeção
- [x] **O Alfredo deixou de chamar de "recebido" o que ainda não caiu**: o dossiê marca cada linha como recebida ou a receber e informa o total confirmado ao lado do previsto
- [x] **Tela**: "todo dia 5 · 120 recebimento(s)" virou **"cairá dia 08 (5º dia útil deste mês)"** — e "recebido em 08/09 (5º dia útil)" depois de confirmado
- [x] **Testes**: 633 no total (24 novos) — 14 do calendário contra datas reais conferidas uma a uma (Páscoa de 4 anos, Carnaval, Corpus Christi, o 5º dia útil de setembro/2026 caindo no dia 8 por causa do feriado da Independência, mês curto caindo no último dia útil), materialização por dia útil, `update` que não reescreve recebimento já confirmado, o toggle, e `@DataJpaTest` provando que a soma do confirmado não cruza usuários. Gate de cobertura do domínio mantido


### Etapa 23 — Gemini em DEV, OpenAI em produção (½ dia)

**Diagnóstico do que existia.** A IA nunca chegou a rodar de verdade nesta máquina: subia sempre em modo demonstração. O runbook faz `set -a; . ./.env; set +a` antes de iniciar a app, e `.env` exportava `AI_API_KEY=` **vazio** — variável de ambiente vence qualquer YAML, então a chave do `.env.local` era ignorada em silêncio. Com a IA desligada, dois defeitos ficaram escondidos atrás dela.

- [x] **Motor por ambiente**: `application-dev.yml` fixa `ai.provider=gemini` e lê a chave de `AI_API_KEY_DEV`; produção continua no OpenAI, pelo bloco `ai` do `application.yml` alimentado pelo orquestrador (e agora explícito no `docker-compose.prod.yml`). Variável **própria** para dev, sem cair para `AI_API_KEY`: mandar chave da OpenAI para o Gemini só renderia 401 e abriria o circuit breaker. Nenhuma chave entra em arquivo versionado — as duas vivem no `.env.local`
- [x] **`AI_PROVIDER`/`AI_API_KEY` saíram do `.env`** (comentados, com o motivo escrito ali). Era isso que mantinha a IA desligada; agora `.env` não sabota mais o `.env.local`
- [x] **Modelo padrão do Gemini corrigido**: `gemini-2.0-flash-lite` foi **retirado** do catálogo e passou a responder 404 — que o app tratava como indisponibilidade e mascarava no texto local, sem nunca dizer que o problema era o nome do modelo. Padrão agora é `gemini-3.5-flash-lite` (o embedding `gemini-embedding-001` segue válido, em 1536 dimensões)
- [x] **Bug: o índice semântico nunca conseguiu gravar.** `VectorIndexService.reindex` era `@Transactional(readOnly = true)` e escrevia — todo INSERT morria com SQLSTATE 25006, engolido pelo `reindexQuietly`. A anotação saiu do serviço (entre ler e gravar há a chamada HTTP de embeddings, e §4.1 proíbe segurar conexão do pool nessa espera) e as escritas ganharam transação própria e curta no `VectorStoreRepository`. Mesmo problema em `purge`
- [x] **Bug: trocar de fornecedor não reindexava.** A impressão digital do índice era só o hash do texto, então vetores da OpenAI continuariam no banco respondendo a buscas feitas com vetores do Gemini — mesma dimensão, espaços diferentes, nenhum erro e resultado ruim. A impressão passou a incluir o **modelo** (`fingerprintsByUser`), e a troca de motor reindexa sozinha
- [x] **Verificado ao vivo contra a API real**: `INSIGHT` e `CHAT` em `gemini-3.5-flash-lite`, `EMBEDDING` em `gemini-embedding-001` com 80 documentos indexados; a resposta do chat ("maior gasto de agosto: Aluguel, R$ 2.200,00, moradia, pago, vence dia 5") confere com o banco. Livro-caixa `ai_usage` registrando as três funcionalidades
- [x] **Testes**: 634 no total, gate de cobertura mantido; novo caso provando que trocar de fornecedor reindexa mesmo sem mudança de texto


### Etapa 24 — Resposta do Alfredo formatada e em streaming (1 dia)

**Diagnóstico do que existia.** O modelo sempre respondeu em Markdown, mas o balão renderizava com `th:text`: o usuário via `**não é recomendado**` com os asteriscos e uma lista numerada virava um parágrafo só. E o envio na tela do Alfredo era POST com recarga de página inteira — vários segundos de tela parada, sem nenhum sinal de vida, e a resposta aparecendo de uma vez.

- [x] **Markdown seguro no cliente** (`markdown.js`): negrito, itálico, código, listas (com e sem número) e títulos. Monta DOM com `createElement`/`textContent`, **nunca `innerHTML`** — o texto vem de um modelo alimentado por dados do usuário, então é conteúdo não confiável (§3.2), e montando nó a nó não há como um atributo de evento virar elemento. A CSP estrita segue valendo sem exceção. O que não estiver no subconjunto aparece literal: nunca some conteúdo
- [x] **Streaming real (SSE), não datilografia simulada.** `POST /api/v1/chats/{id}/messages/stream` devolve `text/event-stream`; o cliente lê com `fetch` + `ReadableStream` (`EventSource` só faz GET). Três eventos: `delta`, `done` (o texto completo, **autoritativo** — a tela re-renderiza com ele, então uma queda no meio nunca deixa resposta pela metade) e `error`. Medido: primeiro pedaço em ~1,2s contra ~1,5s da resposta inteira
- [x] **A contabilidade de tokens sobreviveu ao streaming** (§4.1): o dialeto manda `stream_options.include_usage`, e o `usage` acumulado de cada chunk é registrado no `ai_usage` ao fim. Sem isso, "sempre contabilizar" deixaria de valer justamente na funcionalidade mais usada
- [x] **Streaming não repete.** O `post()` comum tenta de novo em 429/5xx; o streamado não: repetir uma chamada que já escreveu meia resposta na tela duplicaria o texto para quem está lendo
- [x] **Degrada em camadas**: sem JS, a tela do Alfredo continua no POST + redirect de sempre (o form ficou intacto); com JS mas sem streaming disponível, o widget usa o POST JSON; falha do provedor cai no texto de contingência, como antes
- [x] **A primeira mensagem do widget continua sem streaming** — é ela que cria a conversa e define o título. As seguintes streamam
- [x] **Bug pré-existente corrigido**: o atalho Enter-para-enviar da tela do Alfredo apontava para `.mgr-composer .mgr-input`, e esse ancestral não existe no template — o seletor nunca casou com nada e Enter só quebrava linha
- [x] **Testes**: 638 no total (4 novos no cliente de IA: ordem dos pedaços, `usage` do último chunk, chunk malformado que não derruba o fluxo, e a ausência de retry). Verificado ao vivo contra o Gemini nas duas telas


### Etapa 25 — Motor de IA escolhido pelo administrador (1 dia)

**Diagnóstico do que existia.** O fornecedor era decidido no boot: `AiModelClient` recebia um único `AiProvider` e congelava URL, modelos e cliente HTTP em campos `final`. Trocar de motor exigia mudar variável de ambiente e reiniciar — e a chave era uma só, então não dava para ter os dois configurados ao mesmo tempo.

- [x] **Um motor por fornecedor, montado no boot** (`AiEngine`): provider, credencial, URL, modelos e clientes HTTP. O `AiModelClient` deixou de ter campos fixos e resolve o motor ativo a cada chamada — a troca não reconstrói nada em tempo de requisição
- [x] **Uma chave por fornecedor** (`ai.keys.openai`, `ai.keys.gemini`). É o que permite alternar sem reconfigurar, e impede mandar a credencial de um para a API do outro (só renderia 401). A `ai.api-key` continua valendo como fallback de quem usa um motor só. **Nenhum segredo vai para o banco**: o que se grava é qual fornecedor está ativo
- [x] **A escolha persiste** em `app_settings` (changelog `016`), chave/valor de escopo global — sobrevive a restart. `AiProviderSetting` lê com cache e recusa trocar para fornecedor desconhecido ou **sem chave** (isso desligaria a IA inteira em silêncio)
- [x] **Exclusivo de administrador**: `POST /api/admin/ai/provider` — o caminho já exige `ROLE_ADMIN` no `SecurityConfig` e o `@PreAuthorize` repete a exigência na classe, para a regra não depender de uma linha de configuração distante (§3.1). O valor passa por lista fechada de caracteres (§3.3) e a troca entra no **audit log**
- [x] **Interruptor de dois rótulos** no menu do usuário, logo abaixo de "Modo claro/escuro": OpenAI de um lado, Gemini do outro, com o polegar deslizando. Posição **100% em CSS** (grid de colunas iguais + `translateX(100%)`): medir `offsetWidth` em JS dava zero enquanto o menu estava escondido e o polegar nascia no canto errado
- [x] **DEV continua só no Gemini**: `application-dev.yml` fixa `provider=gemini` com `provider-locked=true`, e o seletor aparece desabilitado com o motivo no `title` — some seria pior, o administrador não saberia que a opção existe. `AI_PROVIDER_LOCKED=false` destrava para exercitar a troca localmente. Em produção a escolha é do administrador
- [x] **Consequência declarada na própria UI**: trocar de motor troca o espaço vetorial e o modelo dos resumos, então o índice semântico é reindexado e os resumos regerados. O toast diz isso ao confirmar
- [x] **Testes**: 656 no total (18 novos) — `AiProviderSetting` (fallback do ambiente, escolha gravada, fornecedor extinto, sem chave, ambiente travado) e o endpoint admin (troca + audit, 400 com código de erro, valor fora da lista barrado na validação). Verificado ao vivo: alternar pela UI gravou em `app_settings`, entrou no audit, e a chamada seguinte foi de fato para o outro fornecedor


### Etapa 26 — Acesso à IA liberado usuário a usuário (1 dia)

**Diagnóstico do que existia.** Toda conta autenticada via o Alfredo inteiro — chat flutuante, balão de sugestões, tela do Alfredo e as rotas de chat e resumo. Não havia como restringir a IA a quem deveria tê-la, e cada conta consome tokens.

- [x] **`users.ai_enabled`** (changelog `017`). Contas que já existiam **mantiveram o acesso** (o `DEFAULT true` do `ADD COLUMN` preenche as linhas atuais — tirar acesso é decisão do administrador, não efeito de migração); logo em seguida o default vira `false`: **conta nova nasce sem IA**, inclusive acessor. A entidade repete o mesmo padrão, para que um INSERT fora do JPA não abra acesso por descuido
- [x] **Coluna "IA" na tela de usuários**, com um toggle por linha, e **caixa "Liberar o Alfredo" no cadastro** de usuário (desmarcada por padrão). Só administrador: a rota `/app/users/**` já exige `ROLE_ADMIN` e a troca entra no audit log (`USER_AI_ACCESS_CHANGE`)
- [x] **Barrado no servidor, não só escondido.** `@PreAuthorize("isAuthenticated() and @currentUser.hasAiAccess()")` na tela do Alfredo e nas rotas de chat e resumo; sem acesso, 403. O interceptor do widget deixa de renderizar o orbe (e, sem orbe, não há balão de sugestão) e o item "Alfredo" some do menu — mas isso é conforto: quem digitasse a URL continuaria barrado
- [x] **Retirar o acesso vale na hora.** `CurrentUser.hasAiAccess()` lê a flag do **banco**, não do principal em sessão — senão quem perdeu a IA a manteria até o próximo login. Um cache por requisição mantém isso em uma consulta por página, mesmo com interceptor e `@PreAuthorize` perguntando juntos. Vale a flag de quem está logado: acessor sem IA segue sem IA mesmo operando a conta de alguém que tem
- [x] **Testes**: serviço (conta nova e acessor nascem sem IA, caixa marcada libera, toggle), controller admin (troca + audit), interceptor (sem IA não publica orbe nem resumo) e um `@SpringBootTest` com Spring Security de verdade: 403 na tela e nas rotas de IA, 200 com acesso, e a retirada valendo na mesma sessão

### Etapa 27 — Página de erro própria (½ dia)

**Diagnóstico do que existia.** A pasta `templates/error/` estava vazia: qualquer 403, 404 ou 500 caía na "Whitelabel Error Page" do Spring, sem marca, sem explicação e sem caminho de volta.

- [x] **`error/page.html`** com o logo completo com slogan (sem fundo) centralizado, uma trilha tracejada que termina num ponto dourado (o "rastro" que parou ali) e, abaixo, a mensagem explícita: título, explicação, **código e endereço** ("Código 404 em /app/…") e o botão de volta. Erro 5xx oferece também abrir um chamado no Suporte; 401 leva ao login
- [x] **Sempre clara, mesmo com o app no tema escuro**: o "RASTR" do logo é azul-marinho e o PNG não tem fundo — sobre o fundo escuro do app ele some. A paleta sai do próprio logo. Página **autocontida** (não carrega o shell do app nem `tokens.css`/`base.css`): o erro não pode depender do que talvez tenha falhado
- [x] **`RastroosErrorViewResolver`**: só é consultado para respostas **HTML**, então o JSON de erro de `/api/**` não mudou. **Monta o modelo do zero** — o mapa de entrada traz `message`, `trace` e `exception` quando o perfil dev libera, e um template que recebe isso um dia acaba mostrando; aqui a página só conhece status, endereço e chaves de texto, então não há como vazar detalhe interno (§3.2). Reconhece as rotas do Alfredo: um 403 nelas diz "O Alfredo não está liberado para a sua conta" e orienta a pedir ao administrador. Textos em `messages*.properties` (§8); o 404 não sugere que o registro existe em outra conta (§2.2)
- [x] **A tela do Alfredo voltou ao 403.** Na etapa anterior ela redirecionava ao dashboard só porque não havia página de erro; com uma página que explica o bloqueio, o gate voltou a ser um único `@PreAuthorize` na classe, igual às rotas REST
- [x] **Asset otimizado**: o PNG original (2181px, 730 KB) virou 1200px, servido como WebP de 69 KB via `<picture>` com o PNG de fallback
- [ ] **Anônimo continua indo para o login em qualquer erro**: `/error` exige autenticação no `SecurityConfig`. Mostrar a página para quem não está logado exige liberar `/error` — é relaxar uma regra de segurança, então fica para decisão explícita (§11.3)
- [x] **Testes**: 686 no total — o resolvedor (chave por status e rota, modelo sem `message`/`trace`/`exception`, ações por família de erro) e renderização real da página via `/error` no `@SpringBootTest` (texto do Alfredo, logo, e uma exceção com texto sensível que **não** aparece no HTML)



### Etapa 28 — Cartões & Contas em modal + anexar fatura do cartão (2 dias)

**Diagnóstico do que existia.** Clicar num card abria o detalhe *abaixo* de todos os cards, longe do clique. Não havia como importar uma fatura: cada gasto do cartão era digitado à mão, e a compra parcelada virava N linhas sem nada que as ligasse — apagar "só esta parcela", "desta em diante" ou "todas" era impossível.

- [x] **Detalhe da conta em modal estreito** por cima dos cards (mesmo mecanismo `[data-modal-content]` dos outros modais). Topo fixo com a conta e as ações — **Editar · Anexar fatura · Excluir** — enquanto a lista rola por baixo. Pagar fatura, marcar pago e excluir lançamento voltam com o detalhe aberto (`?open=<conta>`, só abre card que está na tela) e a mensagem do servidor aparece dentro do modal
- [x] **Série de lançamentos** (changelog `018`, `transactions.series_id`): parcelas de uma compra e meses de um gasto fixo passam a ser ligados. Linhas novas já nascem com série; as antigas foram agrupadas por uma regra conservadora — grupo com duas linhas na mesma posição (mesma parcela ou mesmo mês) fica **sem** série e só oferece "apagar este"
- [x] **Excluir lançamento com escopo** (lixeira em cada linha do detalhe): só esta · desta em diante · todas. Sem série, um escopo forjado vira "só este"; o destino de volta é lista fechada (sem redirecionamento aberto)
- [x] **Anexar fatura** (só cartão de crédito, só quem tem o Alfredo liberado, nunca acessor): `InvoiceVisionReader` lê vencimento, total, final do cartão e cada linha com parcela, em saída estruturada estrita. Estorno e pagamento aparecem, mas não viram gasto (valor sempre positivo no banco). PDF com senha recebe mensagem própria
- [x] **Conferência antes de lançar** (decisão do usuário): novo marcado; **já lançado** sem caixa, não duplica; **possível duplicado** (mesmo mês, parcela e valor com outro nome — o lançado à mão) desmarcado; **ajustar valor** para centavos de arredondamento de parcela ainda em aberto. Aviso quando a fatura é de outro final de cartão. Trocar o mês do vencimento refaz o cruzamento no servidor
- [x] **Não duplicar** (`InvoiceReconciler`): cada lançamento existente casa com no máximo uma linha, em passadas do par exato ao aproximado; descrição comparada normalizada (acento, pontuação, truncamento do banco, uma letra trocada). Parcela nova projeta as seguintes nas próximas faturas e reaproveita as que já existem — importar a mesma fatura de novo, a do mês seguinte ou uma anterior depois de uma posterior não cria nada em dobro. Na gravação o cruzamento é **refeito com a conta travada** (`SELECT … FOR UPDATE`): duplo clique, duas abas ou formulário adulterado não duplicam
- [x] **Infra de IA**: funcionalidade `INVOICE` no livro-caixa e circuit breaker próprio, `ai.invoice` (16k tokens, 120s, 300 linhas), sem repetir em timeout, rota no rate limit de IA
- [x] **Bug encontrado na verificação ao vivo**: a camada de compatibilidade do Gemini recusa a parte `file` com PDF ("Invalid content part type: file", 400) — em dev, todo PDF (inclusive o boleto do "Lançar gasto") caía no modo demonstração. `GeminiProvider.filePart` agora manda o PDF como `image_url`; a OpenAI segue com `file`
- [x] **Verificado ao vivo contra o Gemini** com faturas fictícias: outubro lido completo (parcelas 3/10 e 4/6, pagamento e estorno separados, duas corridas iguais mantidas) → 8 lançamentos + 9 parcelas; a mesma fatura de novo → nada; novembro → as parcelas 4/10 e 5/6 reconhecidas como já lançadas, só as 2 compras novas entram
- [x] **Testes**: 770+ no total — cruzamento (17 cenários), leitor, serviço, controllers, `UploadGuard`, 403 sem IA e fluxo completo contra o Postgres (reimportação, mês seguinte, escopos de exclusão e isolamento por usuário). Gate de cobertura do domínio mantido

---

## 7. Estrutura de pastas

```
Rastroos/
├── Projeto.md
├── CLAUDE.md
├── README.md
├── pom.xml
├── docker-compose.yml
├── .env.example
├── .gitignore
├── Dockerfile
├── scripts/
│   ├── db-up.sh
│   └── db-down.sh
└── src/
    ├── main/
    │   ├── java/com/rastroos/
    │   │   ├── RastroosApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── WebMvcConfig.java
    │   │   │   ├── LocaleConfig.java
    │   │   │   ├── RateLimitConfig.java
    │   │   │   ├── CacheConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   └── ThymeleafConfig.java
    │   │   ├── security/
    │   │   │   ├── CustomUserDetailsService.java
    │   │   │   ├── CurrentUser.java                # @AuthenticationPrincipal helper
    │   │   │   ├── BruteForceFilter.java
    │   │   │   ├── SecurityHeadersFilter.java
    │   │   │   ├── LoginSuccessHandler.java
    │   │   │   ├── LoginFailureHandler.java
    │   │   │   ├── PasswordPolicy.java
    │   │   │   └── AuditLogger.java
    │   │   ├── domain/
    │   │   │   ├── entity/
    │   │   │   │   ├── User.java
    │   │   │   │   ├── UserSession.java
    │   │   │   │   ├── LoginAttempt.java
    │   │   │   │   ├── VerificationCode.java
    │   │   │   │   ├── Category.java
    │   │   │   │   ├── Account.java
    │   │   │   │   ├── Transaction.java
    │   │   │   │   ├── Income.java
    │   │   │   │   ├── Investment.java
    │   │   │   │   ├── InvestmentHistory.java
    │   │   │   │   ├── SupportTicket.java
    │   │   │   │   ├── SupportTicketComment.java
    │   │   │   │   ├── Chat.java
    │   │   │   │   ├── ChatMessage.java
    │   │   │   │   └── AuditLog.java
    │   │   │   ├── repository/
    │   │   │   │   └── (um por entity)
    │   │   │   ├── service/
    │   │   │   │   ├── UserService.java
    │   │   │   │   ├── AuthService.java
    │   │   │   │   ├── DashboardService.java
    │   │   │   │   ├── AccountService.java
    │   │   │   │   ├── TransactionService.java
    │   │   │   │   ├── IncomeService.java
    │   │   │   │   ├── InvestmentService.java
    │   │   │   │   ├── ReportService.java
    │   │   │   │   ├── CompareService.java
    │   │   │   │   ├── SupportService.java
    │   │   │   │   ├── ChatService.java
    │   │   │   │   ├── AlfredoAiService.java
    │   │   │   │   └── AdminUserService.java
    │   │   │   └── mapper/
    │   │   │       └── (MapStruct interfaces)
    │   │   └── web/
    │   │       ├── controller/
    │   │       │   ├── LandingController.java
    │   │       │   ├── AuthController.java
    │   │       │   ├── DashboardController.java
    │   │       │   ├── AccountController.java
    │   │       │   ├── TransactionController.java
    │   │       │   ├── IncomeController.java
    │   │       │   ├── InvestmentController.java
    │   │       │   ├── ReportController.java
    │   │       │   ├── CompareController.java
    │   │       │   ├── ManagerController.java
    │   │       │   ├── UserAdminController.java
    │   │       │   ├── SupportController.java
    │   │       │   └── ProfileController.java
    │   │       ├── rest/
    │   │       │   └── (REST @RestController)
    │   │       ├── dto/
    │   │       │   └── (DTOs de saída)
    │   │       ├── form/
    │   │       │   └── (Forms de entrada validados)
    │   │       └── advice/
    │   │           ├── GlobalExceptionHandler.java
    │   │           └── ModelAttributeAdvice.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-test.yml
    │       ├── application-prod.yml
    │       ├── db/
    │       │   └── changelog/
    │       │       ├── db.changelog-master.xml
    │       │       ├── 001-initial-schema.xml
    │       │       ├── 002-seed-categories.xml
    │       │       └── 003-create-default-admin.xml
    │       ├── messages/
    │       │   ├── messages.properties        # PT-BR (default)
    │       │   └── messages_en.properties     # EN
    │       ├── templates/
    │       │   ├── layout/
    │       │   │   └── base.html
    │       │   ├── fragments/
    │       │   │   ├── header.html
    │       │   │   ├── sidebar.html
    │       │   │   ├── topbar.html
    │       │   │   ├── user-menu.html
    │       │   │   ├── period-selector.html
    │       │   │   └── footer.html
    │       │   ├── landing.html
    │       │   ├── auth/
    │       │   │   ├── login.html
    │       │   │   ├── signup.html
    │       │   │   ├── verify.html
    │       │   │   ├── forgot.html
    │       │   │   └── reset.html
    │       │   ├── app/
    │       │   │   ├── dashboard.html
    │       │   │   ├── cards.html
    │       │   │   ├── expenses.html
    │       │   │   ├── income.html
    │       │   │   ├── investments.html
    │       │   │   ├── reports.html
    │       │   │   ├── compare.html
    │       │   │   ├── manager.html
    │       │   │   ├── users.html
    │       │   │   ├── support.html
    │       │   │   └── profile.html
    │       │   ├── modals/
    │       │   │   ├── new-expense.html
    │       │   │   ├── new-income.html
    │       │   │   ├── edit-account.html
    │       │   │   ├── change-password.html
    │       │   │   └── edit-profile.html
    │       │   └── error/
    │       │       ├── 403.html
    │       │       ├── 404.html
    │       │       └── 500.html
    │       └── static/
    │           ├── css/
    │           │   ├── tokens.css
    │           │   ├── base.css
    │           │   ├── layout.css
    │           │   ├── components.css
    │           │   ├── landing.css
    │           │   └── screens/
    │           │       ├── dashboard.css
    │           │       ├── cards.css
    │           │       ├── expenses.css
    │           │       ├── income.css
    │           │       ├── investments.css
    │           │       ├── reports.css
    │           │       ├── compare.css
    │           │       ├── manager.css
    │           │       ├── users.css
    │           │       └── support.css
    │           ├── js/
    │           │   ├── app.js
    │           │   ├── theme.js
    │           │   ├── i18n.js
    │           │   ├── charts.js
    │           │   ├── auth.js
    │           │   └── screens/
    │           │       ├── dashboard.js
    │           │       ├── cards.js
    │           │       ├── expenses.js
    │           │       ├── income.js
    │           │       ├── investments.js
    │           │       ├── reports.js
    │           │       ├── compare.js
    │           │       ├── manager.js
    │           │       ├── users.js
    │           │       └── support.js
    │           ├── images/
    │           └── fonts/
    └── test/
        ├── java/com/rastroos/
        │   ├── domain/service/        # unit tests dos services
        │   ├── domain/repository/     # @DataJpaTest + Testcontainers
        │   ├── web/controller/        # MockMvc
        │   ├── web/rest/
        │   └── security/              # testes de auth, brute force, CSRF
        └── resources/
            ├── application-test.yml
            └── fixtures/
```

---

## 8. Padrões de código e qualidade

### 8.1 Regras invioláveis

1. **Sem HTML/JS/CSS inline**. Tudo em arquivos `.html`, `.css`, `.js` separados.
2. **MVC estrito**: Controller fino, Service grosso, Repository sem regra de negócio.
3. **Toda query de domínio filtra por `userId`** (separação interna por usuário).
4. **DTOs** entre camadas: Entity **nunca** vai pra view nem pro JSON externo.
5. **`@Transactional`** nos services, **não** nos controllers nem repositories.
6. **Bean Validation** em todo Form de entrada.
7. **Sem `String` SQL concatenada** — só JPQL parametrizado, named queries ou Criteria API.
8. **Sem segredos no código** — só `.env` ou variáveis de ambiente.
9. **Logs estruturados**, sem dados sensíveis.
10. **Commits pequenos** com mensagem clara (Conventional Commits sugerido).
11. **Sem Lombok**. Getters, setters, equals/hashCode, toString e construtores escritos à mão (Java nativo).
12. **Injeção de dependência via construtor** (sem `@Autowired` em field). Em classes Spring com um único construtor, o `@Autowired` é implícito; classes com `final` nos campos garantem imutabilidade da dependência.

### 8.2 Convenções

- Pacotes minúsculos, classes `PascalCase`, métodos `camelCase`.
- Diretórios de templates **kebab-case**.
- IDs CSS em `kebab-case`; classes idem.
- Idioma do código: **inglês**. Idioma das mensagens de UI: PT-BR + EN via i18n.

---

## 9. Estratégia de testes

### 9.1 Pirâmide

```
                ┌─────────────────┐
                │   E2E (futuro)  │   Selenium / Playwright
                └────────┬────────┘
            ┌────────────┴────────────┐
            │   Integração (MockMvc)   │   ~20% dos testes
            └────────────┬────────────┘
        ┌────────────────┴────────────────┐
        │       Unitários (services)       │   ~70% dos testes
        └────────────────┬────────────────┘
                         │
            Testes de repository com Testcontainers
```

### 9.2 Tipos

| Tipo | Ferramenta | O quê |
|------|------------|-------|
| **Unitário** | JUnit 5 + Mockito | Cada Service, mappers, regras de negócio puras |
| **Repository** | Spring Test + **Testcontainers (Postgres real)** | Queries, índices, constraints |
| **Web MVC** | `@WebMvcTest` + MockMvc | Controllers (Thymeleaf e REST), security, CSRF |
| **Security** | `@SpringBootTest` + Testcontainers | Login feliz, lockout, CSRF, autorização |
| **Migration** | Liquibase + Testcontainers | Sobe banco zerado e aplica todos os changelogs; valida `liquibase:status` |

### 9.3 Metas

- **Cobertura mínima**: 80% line + 70% branch no pacote `domain`.
- **Mutation testing** (PIT, opcional) ≥ 60%.
- Todo bug fix → teste de regressão antes do fix.

### 9.4 Exemplos obrigatórios

- `TransactionServiceTest`: projeção de parcelas, recorrências, isolamento por usuário (tentar acessar tx de outro usuário → `NotFoundException`).
- `AuthServiceTest`: lockout após 5 falhas, expiração de código de verificação, signup → pending.
- `SecurityFilterChainTest`: rota `/app/**` redireciona para login se anônimo; CSRF inválido bloqueia POST.
- `AdminUserServiceTest`: usuário não-admin → `AccessDeniedException`.

---

## 10. Observabilidade e operações

### 10.1 Logs

- JSON estruturado via Logback.
- Campos obrigatórios: `timestamp`, `level`, `logger`, `traceId`, `userId` (quando autenticado), `message`.

### 10.2 Métricas

- Micrometer → Prometheus em `/actuator/prometheus`.
- KPIs: latência por endpoint, taxa de erro, conexões do pool, falhas de login.

### 10.3 Health

- `/actuator/health` com checagem de DB.
- Liveness + readiness separadas (config Kubernetes-ready).

### 10.4 Tracing (opcional)

- Spring Cloud Sleuth + OpenTelemetry (futuro).

---

## 11. Critérios de aceite

O projeto é considerado **pronto** quando:

- [x] `docker compose up` sobe DB + app e a aplicação responde em `http://localhost:8080`. _(imagem verificada e2e: sobe em prod, health `UP`)_
- [ ] Landing visualmente equivalente ao mockup baixado. _(implementada; validação visual pendente)_
- [ ] Fluxos completos: login, cadastro com verificação, aprovação admin, reset de senha. _(implementados e testados por unidade; e2e do fluxo completo pendente)_
- [x] Todas as 13 telas implementadas e navegáveis.
- [x] Multi-idioma PT/EN funcionando.
- [x] Tema claro/escuro + 18 paletas persistidos por usuário.
- [x] Separação interna por usuário comprovada por testes (acesso cruzado → 404).
- [x] Swagger UI acessível e completo.
- [x] **Zero** HTML / JS / CSS inline.
- [x] Cobertura de testes ≥ 80% no domínio. _(91.4% line / 76.0% branch; gate no `verify`)_
- [ ] OWASP Dependency Check sem CVEs HIGH/CRITICAL. _(plugin no perfil `security`; execução no NVD pendente)_
- [x] CSP restrita ativa em produção.
- [x] Lockout de brute force ativo e testado.
- [x] Audit log persistido para operações sensíveis.

---

## Anexo A — Checklist OWASP Top 10 (2021)

| # | Risco | Onde tratamos |
|---|-------|---------------|
| A01 Broken Access Control | §5.8 — filtro por `userId` + `@PreAuthorize` |
| A02 Cryptographic Failures | §5.1 — BCrypt(12), hash de tokens/códigos |
| A03 Injection (SQLi/XSS) | §5.4 + §5.5 — JPA parametrizada + Thymeleaf escapando + CSP |
| A04 Insecure Design | Modelagem MVC + threat model nesta doc |
| A05 Security Misconfiguration | §5.7 — headers + perfis Spring + `actuator` restrito |
| A06 Vulnerable Components | §5.10 — Dependency Check no CI |
| A07 Identification & Auth Failures | §5.1 + §5.3 — lockout, sessões, CSRF, renovação de sid |
| A08 Software & Data Integrity | Liquibase versionado (changelogs imutáveis + checksum) + commits assinados (futuro) |
| A09 Logging & Monitoring Failures | §5.9 + §10 — audit log + Prometheus |
| A10 SSRF | Não há integração outbound de URL fornecida pelo usuário; integração IA usa endpoint fixo configurável |

---

## Anexo B — Comandos úteis (alvo)

```bash
# subir banco
docker compose up -d postgres

# rodar app (dev)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# rodar testes
./mvnw test

# cobertura
./mvnw verify
open target/site/jacoco/index.html

# swagger
open http://localhost:8080/swagger-ui.html
```

---

**Fim do documento.** Todas as etapas do roadmap estão concluídas (0–27), com uma pendência declarada na 27. Próximos passos sugeridos fora do roadmap inicial: validação visual/e2e da landing e dos fluxos de auth, rodar o OWASP Dependency Check contra o NVD (perfil `security` + chave), e um `EmailService` SMTP/SES real para prod.
