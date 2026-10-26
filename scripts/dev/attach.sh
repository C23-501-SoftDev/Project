#!/bin/bash
set -euo pipefail

# Usage: ./scripts/dev/attach.sh [COMMAND [ARG...]]
# Diagnostic access to the running service; this is not a source development image.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
if [ "$#" -eq 0 ]; then
    set -- sh
fi
printf '%s\n' 'Opening the app runtime. Edit sources in the host checkout, then rebuild the image.'
exec docker compose --project-directory "$PROJECT_ROOT" -f "$PROJECT_ROOT/docker-compose.yml" exec app "$@"
