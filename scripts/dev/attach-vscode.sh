#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE="docker compose --env-file $ENV_FILE"

if command -v code >/dev/null 2>&1; then
  CODE_LAUNCHER="code"
elif [ "$(uname -s)" = "Darwin" ]; then
  VSCODE_BUNDLED_CLI="/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"
  if [ -x "$VSCODE_BUNDLED_CLI" ]; then
    CODE_LAUNCHER="$VSCODE_BUNDLED_CLI"
  else
    CODE_LAUNCHER="open"
    echo "VS Code CLI 'code' not found. Using macOS fallback via 'open -a Visual Studio Code'."
  fi
else
  echo "VS Code CLI 'code' not found. Install it from VS Code: Command Palette -> 'Shell Command: Install code command in PATH'."
  exit 1
fi

# Get container id for app
cid=$($COMPOSE ps -q app 2>/dev/null || true)
if [ -z "$cid" ]; then
  # fallback to docker ps lookup
  cid=$(docker ps -q -f name=kb_app || true)
fi
if [ -z "$cid" ]; then
  echo "App container not running — attempting to start it with 'docker compose up -d'..."
  if ! docker compose --env-file $ENV_FILE up -d 2>/dev/null; then
    echo "Failed to start containers. Start with 'make dev-up' and retry." >&2
    exit 1
  fi

  # Wait for container to appear
  attempts=0
  while [ $attempts -lt 30 ]; do
    cid=$(docker ps -q -f name=kb_app || true)
    if [ -n "$cid" ]; then break; fi
    attempts=$((attempts+1))
    sleep 1
  done
  if [ -z "$cid" ]; then
    echo "Timed out waiting for app container to start." >&2
    exit 1
  fi
fi

# Ensure container still exists and get full id
if ! docker inspect "$cid" >/dev/null 2>&1; then
  echo "App container ($cid) no longer exists. Start it with 'make dev-up' and retry." >&2
  exit 1
fi

# Use full container ID if available
full_id=$(docker inspect --format='{{.Id}}' "$cid" 2>/dev/null || true)
if [ -n "$full_id" ]; then
  id_for_uri=${full_id#sha256:}
else
  id_for_uri=$cid
fi

# Try URI formats with stable container name first.
# Raw full container IDs may be interpreted inconsistently by Dev Containers.
short_id=${id_for_uri:0:12}
name_uri="vscode-remote://attached-container+kb_app"
full_uri="vscode-remote://attached-container+${id_for_uri}"
short_uri="vscode-remote://attached-container+${short_id}"

echo "Attempting to open VS Code. Candidate URIs:"
echo "  1) $name_uri"
echo "  2) $short_uri"
echo "  3) $full_uri"

for uri in "$name_uri" "$short_uri" "$full_uri"; do
  if [ "$CODE_LAUNCHER" = "code" ] && code --folder-uri "$uri" 2>/dev/null; then
    echo "Opened VS Code for container $cid (URI: $uri)"
    exit 0
  fi
  if [ "$CODE_LAUNCHER" = "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" ] && "$CODE_LAUNCHER" --folder-uri "$uri" 2>/dev/null; then
    echo "Opened VS Code for container $cid (URI: $uri)"
    exit 0
  fi
  if [ "$CODE_LAUNCHER" = "open" ] && open -a "Visual Studio Code" --args --folder-uri "$uri" 2>/dev/null; then
    echo "Opened VS Code for container $cid (URI: $uri)"
    exit 0
  fi
done

echo "Failed to open VS Code with any container URI." >&2
echo "Possible reasons: 'code' CLI not in PATH, Remote - Containers extension missing, or the container stopped." >&2
echo "Diagnostics:" >&2
echo "  docker ps -a --filter name=kb_app" >&2
docker ps -a --filter name=kb_app >&2 || true
echo "  docker inspect $cid" >&2
docker inspect "$cid" >&2 || true
echo "Manual URIs:" >&2
echo "  $full_uri" >&2
echo "  $short_uri" >&2
echo "  $name_uri" >&2
exit 1
