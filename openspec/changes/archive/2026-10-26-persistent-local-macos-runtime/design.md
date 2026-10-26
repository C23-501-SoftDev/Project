## Context

The current Mac already has Java 21, PostgreSQL 18.6 and pgvector 0.8.7. The working demonstration uses temporary tooling and storage paths; the user now explicitly requests an installed local environment that survives process and terminal shutdown. Existing document metadata, Git commits, blobs and RAG index describe one corpus and must be moved together.

## Goals / Non-Goals

**Goals:** native tools discoverable in a normal shell; one documented project lifecycle command; persistent owner-controlled configuration and data; unchanged document/RAG behavior; preservation of the current 15-article demonstration corpus; tested stop/start and real local question answering.

**Non-Goals:** launch-at-login services, public networking, Docker/CI changes, production availability or performance guarantees, scheduled backup automation, new API endpoints, Java logic, models, prompts or retrieval rules.

## Decisions

1. **Use native macOS processes and installed toolchains.** Homebrew supplies Maven, Node 24 and Ollama; the installed Java 21/PostgreSQL 18/pgvector combination is reused. `scripts/local/env.sh` makes the selected binaries available to the operator without requiring an editor session. Docker would introduce another database/model environment and is outside the user's current Mac setup.
2. **Keep runtime state in an ignored project directory.** `.local/` contains the PostgreSQL cluster, application configuration, Git/blob storage, Ollama model store, application artifact, logs and process records. Default addresses are `127.0.0.1:55434` for PostgreSQL (`knowledge_base_rag`, `kb_user`), `127.0.0.1:18081` for the app and `127.0.0.1:11434` for Ollama; model data uses `.local/ollama-models`. This avoids dependence on `/private/tmp` and keeps test fixtures separate. Paths are derived from the checkout; the operator must stop the stack before relocating it.
3. **Separate preparation, build and service lifecycle.** `scripts/local/stack.sh setup` creates missing state and local configuration without replacing existing data or secrets, starts its PostgreSQL for database/extension provisioning and leaves only that managed service running so the operator can restore data before app startup. It does not start Ollama/the app or download models. `build` runs Maven clean verify and atomically installs the JAR; `start` checks required files/tools, starts PostgreSQL/Ollama/the app in dependency order and waits for readiness; `stop` stops only managed processes in reverse order; `status` and `logs` explain current state without printing secrets. A foreign listener or mismatched process record is an error, not permission to terminate another process. Startup does not require an interactive Codex session and does not register an automatic login service.
4. **Use explicit local security configuration.** Generate a random JWT secret and database password once, with owner-only local configuration; PostgreSQL password authentication uses the configured local role. Bind all services to loopback, set `OLLAMA_NO_CLOUD=1`, `AI_ENABLED=false` and `NOTIFICATIONS_ENABLED=false`. Enable RAG and Liquibase's `rag` context, using unchanged `qwen3-embedding:0.6b` (1024 dimensions) and `qwen3:4b`. Package/model downloads can require Internet during setup; inference uses the local server.
5. **Perform a controlled one-time migration, not an automatic overwrite.** Quiesce source writers, capture a PostgreSQL dump and matching Git/blob directories, restore into the fresh destination, verify references and only then switch the application's local configuration. Reuse compatible model files in the permanent model store. Leave the original source/snapshot intact for rollback; repeating ordinary setup must not restore the snapshot over newer user edits.
6. **Keep application contracts untouched.** Affected source areas are shell tooling, ignore rules and documentation; controller/service/repository code and Liquibase schema definitions do not change. PostgreSQL provisioning, extension availability and data restoration are operational database actions. Existing migrations still own the application schema. Since no endpoint or request/response contract changes, no new BACKTRACKER API section is required.

## Risks / Trade-offs

- [Incomplete PostgreSQL/Git/blob snapshot] → stop source writers, record counts and referenced version SHAs, compare migrated content and attachments before switching; keep rollback copy.
- [Port already used by the old demo or another app] → inspect listeners and stop only the explicitly identified source/managed process; fail safely on an unrelated listener.
- [Large local model downloads or disk use] → reuse verified installed model files when available, document the permanent model directory and report missing models explicitly.
- [Toolchain drift after Homebrew updates] → record actual installed versions and commands in the final evidence; do not claim immutable binaries merely from a formula name.
- [Secrets or state accidentally committed] → ignore `.local/`, restrict configuration permissions and inspect tracked diff before merge.
- [Process or machine failure] → local state persists, but this change does not provide automatic recovery, scheduled backups or uptime guarantees.

## Migration Plan

1. Record the current app/database/Git/blob/model locations and corpus identity; install missing tools and create the permanent local configuration.
2. Build/verify the unchanged application using installed tooling and a fresh isolated test database, never the persistent user database.
3. Stop source application writers; capture the consistent database/Git/blob set and retain it locally. Restore only into the intended empty destination and make model files available in `.local/ollama-models`.
4. Start the permanent stack, verify loopback listeners and health, compare migrated document/version/blob references and render a saved article. Perform a real RAG request with server citations.
5. Run `stop` followed by `start`; verify the same corpus and another read/RAG smoke check. Test repeated setup/start without data or secret replacement. Record actual results before checking tasks complete.
6. If migration fails, stop destination processes, retain destination evidence and resume the untouched source using its original database/Git/blob set. Do not combine snapshots from different moments.

## Open Questions

No product decision blocks implementation. Final script-selected filenames, installed versions and migration/verification results must be recorded in `docs/LOCAL_SETUP_RU.md` after execution.
