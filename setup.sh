#!/usr/bin/env bash
# Rastro$ — setup de desenvolvimento local.
# Rode este script na raiz do projeto para preparar o ambiente de forma
# automática: valida o JDK e o Maven Wrapper, gera um .env de DEV funcional,
# baixa as dependências e compila, e sobe o PostgreSQL (via scripts/db-up.sh).
#
# Uso:
#   ./setup.sh                 # setup completo
#   ./setup.sh -y              # não-interativo
#   ./setup.sh --no-docker     # não sobe o Postgres
#   ./setup.sh -h              # ajuda
#
# Idempotente: pode ser executado várias vezes com segurança.
# O schema e o admin inicial são criados pelo Liquibase no 1º boot da app.

set -eo pipefail

# ------------------------------------------------------------------------------
# Flags
# ------------------------------------------------------------------------------
ASSUME_YES=false
SKIP_DOCKER=false

for arg in "$@"; do
    case "$arg" in
        -y|--yes)     ASSUME_YES=true ;;
        --no-docker)  SKIP_DOCKER=true ;;
        -h|--help)
            awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "$0"
            exit 0
            ;;
        *)
            echo "Argumento desconhecido: $arg (use -h para ajuda)" >&2
            exit 1
            ;;
    esac
done

# ------------------------------------------------------------------------------
# Plataforma
# ------------------------------------------------------------------------------
case "$(uname -s)" in
    Linux*)              PLATFORM="linux" ;;
    Darwin*)             PLATFORM="macos" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM="windows" ;;
    *)                   PLATFORM="unknown" ;;
esac

if [ "$PLATFORM" = "windows" ]; then
    echo "=========================================="
    echo " Windows detectado ($(uname -s))"
    echo "=========================================="
    echo ""
    echo "Este projeto (Java + Docker) deve ser configurado dentro do WSL2 —"
    echo "shells nativos do Windows (Git Bash/Cygwin) não têm suporte confiável"
    echo "a Docker e caminhos POSIX."
    echo ""
    echo "Passos (uma vez):"
    echo "  1. No PowerShell (Admin):   wsl --install"
    echo "  2. Reinicie e abra o Ubuntu (WSL)."
    echo "  3. Instale o Docker Desktop com integração WSL2 habilitada."
    echo "  4. Clone o repositório DENTRO do WSL (não em /mnt/c) e rode"
    echo "     ./setup.sh lá — ele roda como Linux."
    echo ""
    exit 1
fi
if [ "$PLATFORM" = "unknown" ]; then
    echo "ERRO: plataforma não suportada: $(uname -s)"
    exit 1
fi

# ------------------------------------------------------------------------------
# Saída colorida
# ------------------------------------------------------------------------------
if [ -t 1 ]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; BLUE=''; NC=''
fi

print_success() { echo -e "${GREEN}[OK]${NC} $1"; }
print_error()   { echo -e "${RED}[ERRO]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[!]${NC} $1"; }
print_info()    { echo -e "${BLUE}[i]${NC} $1"; }
print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

# ------------------------------------------------------------------------------
# Progresso (etapa n/total + barra + porcentagem)
# ------------------------------------------------------------------------------
BAR_WIDTH=30
STEP=0

# Total de etapas — o Postgres pode ser pulado com --no-docker.
TOTAL_STEPS=4   # Java, Maven, .env, build
[ "$SKIP_DOCKER" = false ] && TOTAL_STEPS=$((TOTAL_STEPS + 1))   # Postgres

draw_bar() { # $1 = porcentagem (0-100)
    local pct="$1" filled i out=""
    filled=$(( pct * BAR_WIDTH / 100 ))
    for ((i = 0; i < BAR_WIDTH; i++)); do
        if ((i < filled)); then out+="█"; else out+="░"; fi
    done
    printf '%s' "$out"
}

progress_header() { # $1 = título da etapa
    STEP=$((STEP + 1))
    local pct
    pct=$(( STEP * 100 / TOTAL_STEPS ))
    (( pct > 100 )) && pct=100
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}[${STEP}/${TOTAL_STEPS}] ${1}${NC}"
    echo -e "  ${GREEN}$(draw_bar "$pct")${NC} ${pct}%"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

trap 'echo; print_error "Setup interrompido (linha $LINENO). Corrija o problema acima e rode novamente."' ERR

# ------------------------------------------------------------------------------
# Raiz do projeto
# ------------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "pom.xml" ] || ! grep -q "<artifactId>rastroos</artifactId>" pom.xml; then
    print_error "Rode este script na raiz do projeto Rastro\$ (pom.xml do artefato 'rastroos' não encontrado)."
    exit 1
fi

print_header "Rastro\$ — Setup de Desenvolvimento"

# ==============================================================================
# 1. JDK
# ==============================================================================
REQUIRED_JAVA="$(grep -oE '<java.version>[0-9]+' pom.xml | grep -oE '[0-9]+' | head -1)"
[ -z "$REQUIRED_JAVA" ] && REQUIRED_JAVA=25

progress_header "JDK ${REQUIRED_JAVA}"

java_install_hint() {
    print_info "Instale o JDK ${REQUIRED_JAVA} (Temurin recomendado):"
    print_info "  • SDKMAN (multiplataforma): curl -s \"https://get.sdkman.io\" | bash && sdk install java ${REQUIRED_JAVA}-tem"
    if [ "$PLATFORM" = "macos" ]; then
        print_info "  • Homebrew: brew install openjdk@${REQUIRED_JAVA} (siga o aviso de symlink/PATH do brew)"
    else
        print_info "  • Ou baixe em: https://adoptium.net/"
    fi
}

if ! command -v java &> /dev/null; then
    print_error "Java não está instalado."
    java_install_hint
    exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\.[0-9]+)?.*/\1/')"
if [ -z "$JAVA_MAJOR" ] || ! [ "$JAVA_MAJOR" -eq "$JAVA_MAJOR" ] 2>/dev/null; then
    print_warning "Não foi possível detectar a versão do Java ($(java -version 2>&1 | head -1))."
elif [ "$JAVA_MAJOR" -ge "$REQUIRED_JAVA" ]; then
    print_success "Java $(java -version 2>&1 | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
else
    print_error "Java $JAVA_MAJOR é muito antigo (requer $REQUIRED_JAVA+)."
    java_install_hint
    exit 1
fi

# ==============================================================================
# 2. Maven (Wrapper)
# ==============================================================================
progress_header "Maven Wrapper"

if [ -f "./mvnw" ]; then
    chmod +x ./mvnw 2>/dev/null || true
    MVN="./mvnw"
    print_success "Maven Wrapper encontrado (./mvnw)"
elif command -v mvn &> /dev/null; then
    MVN="mvn"
    print_warning "Sem ./mvnw — usando o Maven do sistema ($(mvn -v 2>/dev/null | head -1 | awk '{print $3}'))."
else
    print_error "Nem ./mvnw nem 'mvn' disponíveis."
    exit 1
fi

# ==============================================================================
# 3. Arquivo .env (DEV)
# ==============================================================================
progress_header "Configuração (.env de DEV)"

# Anexa KEY=VALUE ao .env apenas se a chave ainda não estiver ativa (idempotente).
ensure_env() {
    local key="$1" val="$2"
    if grep -qE "^${key}=" .env 2>/dev/null; then
        print_success "${key} já definido no .env (preservado)"
    else
        printf '%s=%s\n' "$key" "$val" >> .env
        print_success "${key} definido no .env"
    fi
}

if [ -f ".env" ]; then
    print_success ".env já existe (preservado — nada alterado)"
else
    if [ ! -f ".env.example" ]; then
        print_error ".env.example não encontrado — impossível gerar o .env."
        exit 1
    fi
    cp .env.example .env
    print_success ".env criado a partir do .env.example"

    # Garante uma quebra de linha final antes de anexar as variáveis ativas.
    [ -n "$(tail -c1 .env 2>/dev/null)" ] && echo >> .env

    # Defaults de DEV que casam com application-dev.yml — o Postgres do compose
    # passa a subir com as MESMAS credenciais que a app espera por padrão.
    ensure_env "POSTGRES_USER" "rastroos"
    ensure_env "POSTGRES_PASSWORD" "change-me-in-local-env"
    ensure_env "POSTGRES_DB" "rastroos"
    ensure_env "POSTGRES_PORT" "5432"
    if command -v openssl &> /dev/null; then
        ensure_env "RASTROOS_APP_SECRET" "$(openssl rand -base64 48 | tr -d '\n')"
    fi
    print_info "Credenciais de DEV (Postgres em 127.0.0.1). Para produção use variáveis do orquestrador — nunca este .env."
fi

# ==============================================================================
# 4. Dependências & build (Maven)
# ==============================================================================
progress_header "Dependências & build (Maven)"

print_info "Baixando dependências e compilando (pode demorar na primeira vez)..."
if $MVN -B -DskipTests package; then
    print_success "Build concluído (JAR em target/)"
else
    print_warning "O build falhou — verifique os erros acima."
fi

# ==============================================================================
# 5. Banco de dados (PostgreSQL)
# ==============================================================================
if [ "$SKIP_DOCKER" = false ]; then
    progress_header "Banco de dados (PostgreSQL)"

    if ! command -v docker &> /dev/null; then
        print_warning "Docker não está instalado. Instale: https://docs.docker.com/get-docker/"
    elif ! docker info &> /dev/null; then
        print_warning "Docker está instalado mas não respondeu."
        if [ "$PLATFORM" = "linux" ]; then
            print_info "No Linux: 'sudo systemctl start docker' e adicione seu usuário ao grupo docker"
            print_info "('sudo usermod -aG docker \$USER' e reabra a sessão)."
        else
            print_info "Inicie o Docker Desktop e rode o setup novamente."
        fi
    elif [ -x "./scripts/db-up.sh" ]; then
        print_info "Subindo o PostgreSQL via scripts/db-up.sh ..."
        if ./scripts/db-up.sh; then
            print_success "PostgreSQL pronto (o schema/admin são criados pelo Liquibase no 1º boot da app)"
        else
            print_warning "scripts/db-up.sh falhou — verifique os logs acima."
        fi
    else
        print_info "Subindo o PostgreSQL via docker compose ..."
        if docker compose up -d postgres; then
            print_success "PostgreSQL iniciado"
        else
            print_warning "Falha ao subir o Postgres — rode 'docker compose up -d postgres' manualmente."
        fi
    fi
else
    print_info "(Postgres pulado por --no-docker)"
fi

# ==============================================================================
# Resumo
# ==============================================================================
trap - ERR
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Setup concluído${NC}"
echo -e "  ${GREEN}$(draw_bar 100)${NC} 100%"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "Próximos passos:"
echo ""
if [ "$SKIP_DOCKER" = true ]; then
    echo -e "  1. Suba o Postgres:       ${GREEN}./scripts/db-up.sh${NC}"
    echo -e "  2. Rode a aplicação:      ${GREEN}${MVN} spring-boot:run${NC}   (perfil dev — padrão)"
    echo -e "  3. Acesse:                ${GREEN}http://localhost:8080${NC}"
else
    echo -e "  1. Rode a aplicação:      ${GREEN}${MVN} spring-boot:run${NC}   (perfil dev — padrão)"
    echo -e "  2. Acesse:                ${GREEN}http://localhost:8080${NC}   ·   Swagger: ${GREEN}/swagger-ui.html${NC}"
fi
echo ""
echo -e "  Admin inicial (Liquibase): ${GREEN}admin@rastroos.local${NC} — senha padrão exige troca no 1º login."
echo -e "  Banco:  ${GREEN}./scripts/db-up.sh${NC} / ${GREEN}./scripts/db-down.sh${NC}   |   Testes: ${GREEN}${MVN} test${NC}"
echo ""
print_success "Ambiente pronto. Bom trabalho!"
