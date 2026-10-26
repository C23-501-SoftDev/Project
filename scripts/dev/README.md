# Docker helper commands

The complete runtime is started from the repository root with `docker compose up -d --build --wait --wait-timeout 1800`. An `.env` file is optional; generated database and JWT secrets are stored in their named volume.

The shell and PowerShell helpers have matching purposes:

| Helper | Action |
| --- | --- |
| `bootstrap.sh` / `bootstrap.ps1` | Build and start the current Compose project with the readiness wait; preserve its volumes and existing optional settings |
| `attach.sh` / `attach.ps1` | Open a diagnostic `sh` in the running `app` service, or execute the supplied command and arguments |
| `attach-vscode.sh` / `attach-vscode.ps1` | Open the host source checkout in VS Code; do not start containers |

The app image contains a JAR and JRE, not the source workspace or Maven. Edit on the host or use the separate [Java development container](../../.devcontainer/README.md). Rebuild and restart with `make dev-up` or the Compose startup command after changes.

`./run-e2e.sh` creates its own uniquely named Compose project, disables real model inference only in that test project, and removes only that project's containers and volumes afterwards. The test override removes Ollama from application startup dependencies, so these browser tests do not download its image or start its server. RAG browser scenarios use mocked answers and do not verify real model quality; the default application stack still requires Ollama and prepared models. Its optional arguments replace the browser command, for example `./run-e2e.sh npx playwright test --grep @strict`. Browser reports are copied to `.local/e2e-results/<test-project>/`. Python 3 and Compose 2.24.4+ are required for this test helper; application startup itself does not require host Python.

Helper checks do not contact a Docker engine:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/dev -p 'test_*.py'
```

Shell lifecycle/error paths are exercised against a fake Docker CLI, including test-project isolation and propagation of the browser exit code. PowerShell is unavailable on the current Mac, so its wrappers receive static contract checks only; a Windows/PowerShell execution pass has not been claimed. Container startup and the full browser suite remain separate acceptance checks.
