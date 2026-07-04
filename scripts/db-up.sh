#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Rastro$ — sobe o PostgreSQL local
# ─────────────────────────────────────────────────────────────
# Uso:
#   ./scripts/db-up.sh
#
# Requer:
#   - Docker + Docker Compose v2
#   - Arquivo .env na raiz (cópia de .env.example)
# ─────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$( cd "${SCRIPT_DIR}/.." && pwd )"
cd "${ROOT_DIR}"

# ── Cores ────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[db-up]${NC} $*"; }
ok()    { echo -e "${GREEN}[db-up]${NC} $*"; }
warn()  { echo -e "${YELLOW}[db-up]${NC} $*"; }
fail()  { echo -e "${RED}[db-up]${NC} $*" >&2; exit 1; }

# ── Pré-requisitos ───────────────────────────────────────────
command -v docker >/dev/null 2>&1 || fail "Docker não encontrado. Instale: https://docs.docker.com/get-docker/"
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 não encontrado. Atualize o Docker."

if [[ ! -f .env ]]; then
  warn ".env não encontrado — copiando .env.example"
  cp .env.example .env
  warn "EDITE o .env com credenciais reais antes de usar em qualquer ambiente compartilhado."
fi

# ── Subir ────────────────────────────────────────────────────
info "Subindo PostgreSQL..."
docker compose up -d postgres

# ── Aguardar healthcheck ────────────────────────────────────
info "Aguardando o banco ficar healthy..."
ATTEMPTS=30
for i in $(seq 1 "${ATTEMPTS}"); do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' rastroos-postgres 2>/dev/null || echo "starting")
  case "${STATUS}" in
    healthy)
      ok "PostgreSQL pronto (tentativa ${i}/${ATTEMPTS})."
      break
      ;;
    unhealthy)
      docker compose logs --tail=50 postgres
      fail "Container unhealthy. Logs acima."
      ;;
    *)
      sleep 2
      ;;
  esac
  if [[ "${i}" == "${ATTEMPTS}" ]]; then
    docker compose logs --tail=50 postgres
    fail "Timeout aguardando o banco ficar healthy."
  fi
done

# ── Resumo ───────────────────────────────────────────────────
ok "Container: rastroos-postgres"
ok "Porta:     ${POSTGRES_PORT:-5432} (host) → 5432 (container)"
ok "Conecte:   psql -h localhost -p ${POSTGRES_PORT:-5432} -U <POSTGRES_USER> -d <POSTGRES_DB>"
