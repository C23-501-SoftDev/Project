#!/bin/bash
# Run browser tests against a disposable Compose project, never the user's stack.
# Optional arguments replace the browser command, e.g.:
# ./run-e2e.sh npx playwright test --grep @strict
set -euo pipefail
umask 077

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
if ! command -v python3 >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    printf '%s\n' 'Python 3 and Docker Compose 2.24.4+ are required for the isolated E2E runner.' >&2
    exit 1
fi

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/kb-e2e.XXXXXXXX")"
PROJECT_NAME="$(basename "$TEMP_DIR" | tr '[:upper:].' '[:lower:]-')"
BROWSER_CONTAINER="${PROJECT_NAME}-browser"
REPORT_DIR="$PROJECT_ROOT/.local/e2e-results/$PROJECT_NAME"
STACK_ATTEMPTED=0
BROWSER_ATTEMPTED=0
# Ignore ambient project/profile selection and .env, including a user's APP_PORT.
unset COMPOSE_PROJECT_NAME COMPOSE_PROFILES COMPOSE_FILE
COMPOSE=(docker compose --project-name "$PROJECT_NAME" --project-directory "$PROJECT_ROOT"
    --env-file /dev/null -f "$PROJECT_ROOT/docker-compose.yml" -f "$TEMP_DIR/override.yml")

cleanup() {
    local status=$?
    trap - EXIT HUP INT TERM
    # An artifact-copy failure must not skip cleanup or hide Playwright's exit code.
    set +e
    if [ "$BROWSER_ATTEMPTED" -eq 1 ]; then
        local owner
        owner="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' \
            "$BROWSER_CONTAINER" 2>/dev/null || true)"
        if [ "$owner" = "$PROJECT_NAME" ]; then
            if mkdir -p "$REPORT_DIR"; then
                docker cp "$BROWSER_CONTAINER:/e2e/playwright-report" "$REPORT_DIR/" 2>/dev/null || true
                docker cp "$BROWSER_CONTAINER:/e2e/test-results" "$REPORT_DIR/" 2>/dev/null || true
                printf 'Browser artifact directory: %s\n' "$REPORT_DIR"
            else
                printf 'Cannot save browser artifacts to %s; continuing test-project cleanup.\n' "$REPORT_DIR" >&2
            fi
        fi
    fi
    if [ "$STACK_ATTEMPTED" -eq 1 ]; then
        # All volume names were checked before startup; only this generated project is removed.
        if ! "${COMPOSE[@]}" --profile e2e down --volumes --remove-orphans; then
            printf 'Cleanup failed for test project %s; inspect that project only.\n' "$PROJECT_NAME" >&2
            if [ "$status" -eq 0 ]; then status=1; fi
        fi
    fi
    rm -rf "$TEMP_DIR"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

cat > "$TEMP_DIR/override.yml" <<'YAML'
services:
  app:
    ports: !override []
    depends_on: !override
      runtime-init:
        condition: service_completed_successfully
      postgres:
        condition: service_healthy
      models-init:
        condition: service_completed_successfully
    environment:
      RAG_ENABLED: "false"
  models-init:
    depends_on: !override {}
    environment:
      SKIP_MODEL_INIT: "true"
YAML

"${COMPOSE[@]}" --profile e2e config --format json > "$TEMP_DIR/config.json"
python3 - "$PROJECT_NAME" "$TEMP_DIR/config.json" <<'PYTHON_GUARD'
import json
from pathlib import Path
import sys

project, config_file = sys.argv[1:]
config = json.loads(Path(config_file).read_text())

def reject(message):
    raise SystemExit("Unsafe E2E configuration: " + message + "; no containers were started.")

if config.get("name") != project:
    reject("the generated project name was not retained")
for kind in ("volumes", "networks"):
    for key, resource in config.get(kind, {}).items():
        if resource.get("external") or not resource.get("name", "").startswith(project + "_"):
            reject(kind + "/" + key + " is not owned by the test project")
for name, service in config.get("services", {}).items():
    if service.get("container_name") or service.get("ports"):
        reject(name + " has a fixed container name or published port")
    for mount in service.get("volumes", []):
        if mount.get("type") != "volume" or mount.get("source") not in config.get("volumes", {}):
            reject(name + " has a host/shared/anonymous data mount")
services = config.get("services", {})
if services.get("app", {}).get("environment", {}).get("RAG_ENABLED") != "false":
    reject("application RAG must be explicitly disabled for mocked browser scenarios")
if services.get("models-init", {}).get("environment", {}).get("SKIP_MODEL_INIT") != "true":
    reject("the isolated browser project must skip model downloads/inference")
if "e2e" not in services:
    reject("browser service is missing")
expected_dependencies = {
    "runtime-init": "service_completed_successfully",
    "postgres": "service_healthy",
    "models-init": "service_completed_successfully",
}
app_dependencies = services.get("app", {}).get("depends_on", {})
if not isinstance(app_dependencies, dict) or set(app_dependencies) != set(expected_dependencies):
    reject("application startup must depend only on the isolated database and initialization jobs")
for name, condition in expected_dependencies.items():
    dependency = app_dependencies[name]
    if not isinstance(dependency, dict) or dependency.get("condition") != condition or dependency.get("required") is False:
        reject("application readiness dependency is missing for " + name)
if services["models-init"].get("depends_on"):
    reject("the skipped model initializer must not start another service")
pending, visited = ["app"], set()
while pending:
    name = pending.pop()
    if name in visited:
        continue
    if name == "ollama":
        reject("mocked browser scenarios must not pull or start Ollama, even transitively")
    if name not in services:
        reject("startup dependency is missing: " + name)
    visited.add(name)
    dependencies = services[name].get("depends_on", {})
    if not isinstance(dependencies, dict):
        reject("startup dependencies must be resolved mappings")
    pending.extend(dependencies)
PYTHON_GUARD

printf 'E2E project: %s (separate database/content/secrets; Ollama and real model inference disabled)\n' "$PROJECT_NAME"
STACK_ATTEMPTED=1
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 600 app
BROWSER_ATTEMPTED=1
set +e
"${COMPOSE[@]}" --profile e2e run --name "$BROWSER_CONTAINER" --no-deps --build e2e "$@"
RESULT=$?
set -e
printf 'Browser test command exit code: %s\n' "$RESULT"
exit "$RESULT"
