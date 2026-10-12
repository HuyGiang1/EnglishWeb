#!/usr/bin/env bash
set -euo pipefail

# Resolve repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

COMPOSE_FILE="${REPO_ROOT}/infra/docker-compose.yml"
ENV_FILE="${REPO_ROOT}/infra/.env"

if [ ! -f "${ENV_FILE}" ]; then
  ENV_FILE="${REPO_ROOT}/infra/.env.example"
fi

echo "==> Stopping EnglishFlow infrastructure services (preserving data volumes)..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" down

echo "==> Infrastructure stopped safely."
