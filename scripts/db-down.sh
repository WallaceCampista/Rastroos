#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Rastro$ — para o PostgreSQL local
# ─────────────────────────────────────────────────────────────
# Uso:
#   ./scripts/db-down.sh           # para o container (preserva dados)
#   ./scripts/db-down.sh --reset   # para + APAGA volume (cuidado!)
#
# Por padrão NÃO apaga o volume, para não perder dados de dev sem aviso.
# ─────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$( cd "${SCRIPT_DIR}/.." && pwd )"
cd "${ROOT_DIR}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[db-down]${NC} $*"; }
ok()    { echo -e "${GREEN}[db-down]${NC} $*"; }
warn()  { echo -e "${YELLOW}[db-down]${NC} $*"; }
fail()  { echo -e "${RED}[db-down]${NC} $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "Docker não encontrado."

RESET="false"
for arg in "$@"; do
  case "${arg}" in
    --reset|-r)
      RESET="true"
      ;;
    -h|--help)
      sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      fail "Argumento desconhecido: ${arg} (use -h para ajuda)"
      ;;
  esac
done

if [[ "${RESET}" == "true" ]]; then
  warn "Modo RESET — o volume rastroos-pgdata será APAGADO."
  read -r -p "Confirma? Digite 'sim' para continuar: " ans
  if [[ "${ans}" != "sim" ]]; then
    info "Cancelado."
    exit 0
  fi
  info "Parando e removendo containers + volumes..."
  docker compose down -v
  ok "Banco parado e volume removido. Próximo db-up.sh começará vazio."
else
  info "Parando containers (volume preservado)..."
  docker compose down
  ok "Banco parado. Dados preservados no volume rastroos-pgdata."
fi
