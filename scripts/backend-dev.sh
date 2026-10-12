#!/usr/bin/env bash
set -euo pipefail

# Resolve repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

INFRA_ENV="${REPO_ROOT}/infra/.env"
BACKEND_ENV="${REPO_ROOT}/backend/.env"

# Source database credentials from infra/.env or backend/.env without exposing secrets
if [ -f "${INFRA_ENV}" ]; then
  set -a
  # shellcheck source=/dev/null
  source "${INFRA_ENV}"
  set +a
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-englishflow}"
  export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-englishflow}"
  export SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD:-}"
elif [ -f "${BACKEND_ENV}" ]; then
  set -a
  # shellcheck source=/dev/null
  source "${BACKEND_ENV}"
  set +a
else
  echo "==> [WARN] Neither ${INFRA_ENV} nor ${BACKEND_ENV} found. Relying on active shell environment."
fi

export SERVER_PORT="${SERVER_PORT:-8080}"

echo "==> Starting EnglishFlow Backend Service on port ${SERVER_PORT}..."
cd "${REPO_ROOT}/backend"
exec ./mvnw spring-boot:run
