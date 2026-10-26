#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
if ! docker compose version >/dev/null 2>&1; then
    printf '%s\n' 'Docker Compose is required. See docs/DOCKER_LOCAL_RU.md for engine setup.' >&2
    exit 1
fi

printf '%s\n' 'Building and starting the persistent local stack; first model preparation can take several minutes.'
exec docker compose --project-directory "$PROJECT_ROOT" -f "$PROJECT_ROOT/docker-compose.yml" \
    up -d --build --wait --wait-timeout 1800
