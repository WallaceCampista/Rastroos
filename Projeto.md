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

**Fim do documento.** Todas as 19 etapas do roadmap estão concluídas (0–19). Próximos passos sugeridos fora do roadmap inicial: validação visual/e2e da landing e dos fluxos de auth, rodar o OWASP Dependency Check contra o NVD (perfil `security` + chave), e um `EmailService` SMTP/SES real para prod.
