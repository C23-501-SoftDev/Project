#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

wait_for_healthy() {
    local container="$1"
    local timeout="${2:-120}"
    local elapsed=0
    local interval=5

    while [ "$elapsed" -lt "$timeout" ]; do
        local status
        status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo "missing")

        if [ "$status" = "healthy" ]; then
            echo -e "${GREEN}✓ ${container} is healthy${NC}"
            return 0
        fi

        sleep "$interval"
        elapsed=$((elapsed + interval))
        echo -e "${YELLOW}  waiting for ${container} (${status})...${NC}"
    done

    echo -e "${RED}✗ timeout waiting for ${container}${NC}"
    docker compose --env-file "$ENV_FILE" logs --tail=40 app postgres 2>/dev/null || true
    return 1
}

if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}✗ ${ENV_FILE} not found. Copy .env.example to .env first.${NC}"
    exit 1
fi

if ! docker compose version &>/dev/null; then
    echo -e "${RED}✗ Docker Compose v2 is required${NC}"
    exit 1
fi

cd "$SCRIPT_DIR"

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  E2E run (fresh database)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"

echo -e "\n${YELLOW}[1/4]${NC} Stopping app and postgres..."
docker compose --env-file "$ENV_FILE" stop app postgres 2>/dev/null || true
docker compose --env-file "$ENV_FILE" rm -f app postgres 2>/dev/null || true

echo -e "\n${YELLOW}[2/4]${NC} Removing PostgreSQL volume..."
while IFS= read -r vol; do
    [ -n "$vol" ] || continue
    echo "  removing volume: ${vol}"
    docker volume rm -f "$vol" 2>/dev/null || true
done < <(docker volume ls -q -f name=postgres_data)

echo -e "\n${YELLOW}[3/4]${NC} Starting app and postgres..."
docker compose --env-file "$ENV_FILE" up -d --build app postgres

wait_for_healthy kb_postgres 60
wait_for_healthy kb_app 180

echo -e "\n${YELLOW}[4/4]${NC} Running E2E tests..."
set +e
if [ "$#" -gt 0 ]; then
    docker compose --env-file "$ENV_FILE" --profile e2e run --rm --build e2e "$@"
else
    docker compose --env-file "$ENV_FILE" --profile e2e run --rm --build e2e
fi
exit_code=$?
set -e

if [ "$exit_code" -eq 0 ]; then
    echo -e "\n${GREEN}✓ E2E tests passed${NC}"
else
    echo -e "\n${RED}✗ E2E tests failed (exit code ${exit_code})${NC}"
fi

exit "$exit_code"
