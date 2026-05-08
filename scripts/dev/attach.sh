#!/usr/bin/env bash
set -euo pipefail

# Usage: ./attach.sh [SHELL]
# Opens an interactive shell in the kb_app container and cds to /workspace

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE="docker compose --env-file $ENV_FILE"
SHELL_IN_CONTAINER="${1:-sh}"

# Prefer using docker compose exec; if container not running, fail with message
if ! $COMPOSE ps --services --filter "status=running" | grep -q '^app$'; then
  echo "Error: app container not running. Start it with 'make dev-up' or 'docker compose up -d'"
  exit 1
fi

exec $COMPOSE exec app sh -c "cd /workspace && exec $SHELL_IN_CONTAINER"
