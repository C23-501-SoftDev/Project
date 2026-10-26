#!/bin/bash
set -euo pipefail

# The runtime contains a packaged JAR, so edit the local checkout in VS Code.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
printf '%s\n' 'Opening the local source checkout. Rebuild with make build or make dev-up after edits.'
if command -v code >/dev/null 2>&1; then
    exec code "$PROJECT_ROOT"
fi
if [ "$(uname -s)" = Darwin ]; then
    VSCODE_CLI='/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code'
    if [ -x "$VSCODE_CLI" ]; then
        exec "$VSCODE_CLI" "$PROJECT_ROOT"
    fi
    exec open -a 'Visual Studio Code' "$PROJECT_ROOT"
fi
printf '%s\n' 'VS Code CLI was not found. Install its code command or open this checkout in your editor.' >&2
exit 1
