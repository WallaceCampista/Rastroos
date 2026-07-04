<h1 align="center">💸 Rastro$</h1>

<p align="center">
  <strong>Sistema Web de Controle Financeiro Pessoal Multi-usuário</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25 LTS"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.6-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.5.6"/>
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 16"/>
  <img src="https://img.shields.io/badge/Thymeleaf-3.1-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white" alt="Thymeleaf"/>
  <img src="https://img.shields.io/badge/Liquibase-4.x-2962FF?style=for-the-badge&logo=liquibase&logoColor=white" alt="Liquibase"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
</p>

<p align="center">
  <a href="#-sobre-o-projeto">Sobre</a> •
  <a href="#-funcionalidades">Funcionalidades</a> •
  <a href="#-tecnologias">Tecnologias</a> •
  <a href="#-arquitetura">Arquitetura</a> •
  <a href="#-instalação">Instalação</a> •
  <a href="#-uso">Uso</a> •
  <a href="#-segurança">Segurança</a> •
  <a href="#-licença">Licença</a>
</p>

---

## 📋 Sobre o Projeto

O **Rastro$** é um sistema web para **controle financeiro pessoal**, com gestão de **despesas, receitas, cartões, contas recorrentes e investimentos** (cofrinhos e carteira), além de **relatórios**, **comparativo entre períodos**, **gestão de usuários** (admin) e **suporte via tickets**. Inclui um assistente de IA financeira (**Alfredo**) e suporte a multi-idioma (PT-BR / EN).

A separação entre usuários é **interna por código** (filtro por `user_id` em todas as queries de domínio), **sem multi-tenant físico** — cada usuário só enxerga os próprios dados, reforçado por testes de isolamento.

### 🎯 Objetivo

Dar ao usuário uma visão clara e segura das suas finanças pessoais — para onde vai o dinheiro, quanto entra, o que está pago e o que vence — com **fidelidade visual** ao design de referência e **segurança de primeira classe**, já que o sistema trata de **dinheiro real**.

> 📘 **Documento mestre:** [`Projeto.md`](./Projeto.md) — escopo completo, etapas e arquitetura.
> 📘 **Regras de desenvolvimento:** [`CLAUDE.md`](./CLAUDE.md) — leia antes de contribuir.

---

## ✨ Funcionalidades

### 🔐 Autenticação & Contas de Usuário

- ✅ Login, cadastro, **verificação por código de e-mail** e aprovação por admin
- ✅ Recuperação e redefinição de senha + troca de senha no perfil
- ✅ **Lockout** por e-mail e **rate limit** por IP (Bucket4j) contra brute force
- ✅ **Audit log** de login, logout, falhas e mudanças sensíveis

### 📊 Dashboard (Visão geral)

- ✅ **KPIs**: recebido, gasto, pago, a pagar e saldo
- ✅ **Gráficos**: linha de evolução do saldo e donut por categoria
- ✅ Lista de **vencimentos próximos** e resumo por conta

### 💳 Cartões & Contas

- ✅ Listagem de cartões e contas com saldo da fatura
- ✅ Dia de fechamento/vencimento e gestão **CRUD**

### 💰 Despesas (Gastos Variáveis)

- ✅ Listagem com filtros, **marcar como pago**, editar e deletar
- ✅ Lançamento com **parcelas e recorrência**

### 📈 Receitas

- ✅ Listagem, edição e novo lançamento de receita

### 🏦 Investimentos

- ✅ **Cofrinhos** (metas) e **carteira** (CDB, Tesouro, LCI, limite garantido)
- ✅ Histórico e **rendimento mensal**

### 🌐 Transversais

- ✅ **Multi-idioma** PT-BR (padrão) / EN via `MessageSource`
- ✅ **Tema** claro/escuro, paletas de cor, densidade visual e seletor de período
- ✅ **Ocultar valores** — toggle global de privacidade que mascara montantes na UI

> 🚧 **Em construção (roadmap):** Relatórios & Comparativo, Suporte (tickets), **Alfredo (IA)**, Admin (gestão de usuários), documentação Swagger, hardening final, observabilidade e deploy. Veja o [Roadmap](#-roadmap).

---

## 🛠️ Tecnologias

### Backend

| Tecnologia | Versão | Descrição |
|------------|--------|-----------|
| **Java** | 25 LTS | Linguagem principal (sem Lombok — código nativo) |
| **Spring Boot** | 3.5.6 | Framework base |
| **Spring Web MVC** | — | Controllers Thymeleaf + REST |
| **Spring Security** | 6.x | Autenticação, autorização e proteções padrão |
| **Spring Data JPA + Hibernate** | — | Persistência ORM parametrizada |
| **Spring Validation** | — | Bean Validation (`@Valid`) nos DTOs/forms |
| **Liquibase** | 4.x | Migrations versionadas (changelogs XML) |
| **MapStruct** | 1.6.3 | Mapeamento Entity ↔ DTO sem boilerplate |
| **Bucket4j** | 8.10.1 | Rate limiting (defesa de brute force) |
| **springdoc-openapi** | 2.7.0 | Documentação da API (Swagger UI) |
| **Spring Boot Actuator** | — | Health, info e métricas |

### Frontend

| Tecnologia | Descrição |
|------------|-----------|
| **Thymeleaf** | Template engine server-side (fragments para reuso) |
| **HTML5 / CSS3** | Arquivos separados, **sem inline** (§2.3 do CLAUDE.md) |
| **JavaScript** | Módulos por tela em `static/js/`, sem `onclick`/`<script>` inline |
| **Chart.js** | Gráficos (linha, donut, barras) |

### Banco de Dados

| Tecnologia | Versão | Descrição |
|------------|--------|-----------|
| **PostgreSQL** | 16 | Banco relacional em container Docker |

### DevOps, Testes & Build

| Tecnologia | Descrição |
|------------|-----------|
| **Maven** | Build e gerenciamento de dependências (wrapper `./mvnw`) |
| **Docker + Docker Compose** | PostgreSQL em desenvolvimento |
| **JUnit 5 + Mockito + MockMvc** | Testes unitários e de integração leve |
| **Testcontainers** | Testes de repositório/integração com Postgres real |
| **JaCoCo** | Cobertura de testes (≥ 80% line no domínio) |

---

## 🏗️ Arquitetura

**MVC estrito** server-side com separação clara de responsabilidades: *Controller fino · Service grosso (`@Transactional`) · Repository sem regra*. A `Entity` **nunca** atravessa a camada Web — sempre convertida para DTO/Form/ViewModel via MapStruct.

```
View (Thymeleaf) ⇄ Controller ⇄ Service ⇄ Repository ⇄ PostgreSQL
```

```
src/main/
├── java/com/rastroos/
│   ├── config/        # SecurityConfig, LocaleConfig, OpenApiConfig, ClockConfig
│   ├── security/      # filtros, lockout, brute force, audit, password policy
│   ├── domain/
│   │   ├── entity/    # entidades JPA + enums
│   │   ├── repository/# Spring Data JPA (findByIdAndUserId...)
│   │   ├── service/   # regra de negócio + @Transactional
│   │   ├── mapper/    # MapStruct (Entity ↔ DTO)
│   │   └── exception/ # exceções de domínio
│   └── web/
│       ├── controller/# controllers Thymeleaf
│       ├── rest/      # endpoints REST (/api/v1)
│       ├── dto/       # objetos de transporte / view models
│       ├── form/      # forms de entrada (@Valid)
│       └── advice/    # GlobalExceptionHandler
└── resources/
    ├── db/changelog/  # Liquibase (master + changelogs por versão)
    ├── messages/      # i18n PT/EN
    ├── templates/     # Thymeleaf (layout, fragments, app/, auth/, modals/)
    └── static/        # css/ (tokens, base, screens/), js/, images/, fonts/
```

### 🔑 Decisões de arquitetura

- **Separação por usuário (não multi-tenant):** toda query de domínio filtra por `user_id`; acesso a recurso de outro usuário retorna **404** (não vaza existência).
- **Dinheiro como `BIGINT` (centavos):** conversão para `BigDecimal` apenas no DTO — sem erro de ponto flutuante.
- **Tempo em UTC** (`TIMESTAMPTZ`); conversão para o fuso do usuário só na apresentação.
- **IDs `UUID`** para entidades de domínio; `BIGSERIAL` apenas em tabelas internas (audit, login_attempts).

---

## 🚀 Instalação

### Pré-requisitos

- **Java 25** (com `JAVA_HOME` apontando para ele)
- **Docker** + **Docker Compose** (para o PostgreSQL)
- **Maven 3.9+** (ou o wrapper `./mvnw`)

```bash
java -version          # deve indicar 25+
docker compose version
```

### 1️⃣ Clone o repositório

```bash
git clone git@github.com:WallaceCampista/Rastroos.git
cd Rastroos
```

### 2️⃣ Configure as variáveis de ambiente

```bash
cp .env.example .env
```

Preencha `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` e o `RASTROOS_APP_SECRET` (ex.: `openssl rand -base64 48`).

### 3️⃣ Suba o PostgreSQL

```bash
docker compose up -d postgres
```

### 4️⃣ Rode a aplicação (perfil dev)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

As migrations do Liquibase rodam no boot. Aplicação em: **http://localhost:8080**

> O admin inicial é criado uma vez pelo changelog `003-create-default-admin.xml` com `password_must_change=true` — o primeiro login serve apenas para trocar a senha.

---

## 📖 Uso

### Acessos

- **Aplicação:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html

### Comandos frequentes

```bash
# Rodar todos os testes
./mvnw test

# Testes + cobertura (JaCoCo)
./mvnw verify
open target/site/jacoco/index.html

# OWASP Dependency Check
./mvnw org.owasp:dependency-check-maven:check

# Parar o banco (preserva volume)
docker compose down
```

---

## 🔐 Segurança

Segurança é **prioridade máxima** — o sistema trata de dinheiro real. Em caso de dúvida, escolhe-se sempre o caminho mais seguro:

- **Senhas:** `BCryptPasswordEncoder` (cost 12); política de senha forte (`PasswordPolicy`)
- **Autenticação:** verificação por código de e-mail + aprovação por admin; renovação de session id no login
- **Brute force:** rate limit por IP (Bucket4j), **lockout** por e-mail após N falhas, `login_attempts` registrado, alertas no audit log
- **SQL injection (defesa em profundidade):** JPA parametrizado → Bean Validation → whitelist de caracteres → usuário de app sem DDL
- **XSS/CSRF:** escape do Thymeleaf (sem `th:utext` com input do usuário), **CSRF** habilitado em todos os forms
- **Headers:** HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` e **CSP restrita** (`default-src 'self'`, sem `unsafe-inline` — por isso a regra "nada inline")
- **Cookies:** `Secure`, `HttpOnly`, `SameSite=Lax`
- **Autorização:** `@PreAuthorize("hasRole('ADMIN')")` nas rotas admin; ownership validado em todo recurso
- **Auditoria:** audit log para login/logout, falhas, mudanças de senha/role e CRUD sensível

---

## 🧪 Testes

Pirâmide de testes: ~70% unitários (Service), ~25% integração leve (`@WebMvcTest`/`@DataJpaTest`), ~5% integração completa (`@SpringBootTest` + Testcontainers).

```bash
./mvnw test        # unitários + integração leve
./mvnw verify      # + cobertura JaCoCo (≥ 80% line no domínio)
```

Toda feature inclui teste de **isolamento por usuário** (usuário B não vê dados do A) e de **autorização** (não-admin bloqueado em rota admin).

---

## 🗺️ Roadmap

Implementação **incremental por etapas** (detalhe em [`Projeto.md`](./Projeto.md) §6):

| # | Etapa | Status |
|:-:|-------|:------:|
| 0 | Bootstrap do repositório | ✅ |
| 1 | Infra Docker + PostgreSQL | ✅ |
| 2 | Esqueleto Spring Boot | ✅ |
| 3 | Schema + Liquibase | ✅ |
| 4 | Entities + Repositories | ✅ |
| 5 | Spring Security + Autenticação | ✅ |
| 6 | Templates Thymeleaf + assets | ✅ |
| 7 | Dashboard + Cartões/Contas | ✅ |
| 8 | Despesas (gastos variáveis) | ✅ |
| 9 | Receitas | ✅ |
| 10 | Investimentos | ✅ |
| 11 | Relatórios + Comparativo | ⏳ |
| 12 | Suporte (tickets) | ⏳ |
| 13 | Alfredo (Gerente IA) | ⏳ |
| 14 | Admin: gestão de usuários | ⏳ |
| 15 | Swagger + docs da API | ⏳ |
| 16 | Hardening final de segurança | ⏳ |
| 17 | Observabilidade | ⏳ |
| 18 | Cobertura + qualidade | ⏳ |
| 19 | Deploy de referência | ⏳ |

> **Legenda:** ✅ concluída · ⏳ pendente

---

## 🤝 Contribuindo

1. Antes de qualquer alteração, leia [`CLAUDE.md`](./CLAUDE.md).
2. Cada feature deve incluir **testes** na mesma alteração.
3. Mudança de schema → **novo changelog Liquibase** (nunca editar um já aplicado — o checksum quebra).
4. **Sem HTML/CSS/JS inline**; injeção de dependência via construtor; sem Lombok.
5. Commits pequenos e descritivos (Conventional Commits sugerido).

---

## 📚 Documentação Complementar

| Documento | Conteúdo |
|-----------|----------|
| [`Projeto.md`](./Projeto.md) | Plano de implementação completo (escopo, dados, segurança, etapas) |
| [`CLAUDE.md`](./CLAUDE.md) | Regras de desenvolvimento e convenções |

---

## 📄 Licença

Privado — projeto pessoal. Todos os direitos reservados.

---

## 👨‍💻 Autor

Desenvolvido por **Wallace Campista**.

---

<p align="center">
  <img src="https://img.shields.io/badge/Status-Em%20Desenvolvimento-6DB33F?style=for-the-badge" alt="Status"/>
</p>
