#!/usr/bin/env bash
set -euo pipefail

# Resolve repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

COMPOSE_FILE="${REPO_ROOT}/infra/docker-compose.yml"
ENV_FILE="${REPO_ROOT}/infra/.env"
ENV_EXAMPLE="${REPO_ROOT}/infra/.env.example"

if [ ! -f "${ENV_FILE}" ]; then
  echo "==> [WARN] ${ENV_FILE} not found."
  if [ -f "${ENV_EXAMPLE}" ]; then
    echo "==> Copying ${ENV_EXAMPLE} to ${ENV_FILE}..."
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    echo "==> Please review and update credentials in ${ENV_FILE} as needed."
  else
    echo "==> [ERROR] Neither ${ENV_FILE} nor ${ENV_EXAMPLE} found. Aborting."
    exit 1
  fi
fi

echo "==> Starting EnglishFlow infrastructure services..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d

echo "==> Waiting for database health check..."
TIMEOUT=30
ELAPSED=0
STATUS=""

while [ "${ELAPSED}" -lt "${TIMEOUT}" ]; do
  STATUS=$(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps postgres --format "{{.Health}}" 2>/dev/null || echo "")
  if [ "${STATUS}" = "healthy" ]; then
    echo "==> PostgreSQL container is healthy!"
    break
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
  echo "    Checking health... (${ELAPSED}s / ${TIMEOUT}s, status: ${STATUS:-starting})"
done

if [ "${STATUS}" != "healthy" ]; then
  echo "==> [WARN] Container did not reach healthy state within ${TIMEOUT}s."
  echo "==> Current container state:"
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps
  exit 1
fi

echo "==> Service status:"
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps
