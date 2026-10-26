## Why

The user wants the complete application, database and local AI to start from a checked-out repository with one Docker Compose command. The existing container instructions require manual environment/SSH configuration and do not prepare pgvector or local RAG models; the verified native Mac workflow remains available as a fallback.

Feature ID: `one-command-docker-stack` (new entry to register after verification).

## What Changes

- Make `docker compose up -d --build` work without an `.env` file, with dependency readiness and an optional explicit `--wait --wait-timeout 1800` form.
- Build Java in a Maven/JDK 21 stage and run the app as a non-root user in JRE 21; isolate PostgreSQL 18 with pgvector and Ollama inside Compose.
- Generate database/JWT secrets once in a persistent volume, initialize local models automatically, and preserve database/content/models/secrets across ordinary down/up.
- Expose only the application on host loopback, disable external AI/SMTP and Ollama cloud features, and remove host source/SSH/Git-config mounts from the default runtime.
- Provide accurate Docker onboarding, actual cold/repeated start and persistence evidence, and a controlled one-time migration of this Mac's complete current native dataset, including recent user edits and attachments, without making migration an automatic fresh-start action.

## Capabilities

### New Capabilities

- `docker-runtime`: One-command local Compose lifecycle with readiness, persistent isolated data/secrets/models and verified migration.

### Modified Capabilities

None. Existing application API, content, authentication, export and RAG behavior remain unchanged.

## Impact

Affected areas: Docker/Compose build and entrypoint files, container setup/verification tooling, README and `docs/DOCKER_LOCAL_RU.md`. The current Mac uses Colima with an ARM64 Virtualization.framework VM (6 CPUs, 12 GiB); application defaults to `127.0.0.1:8080`, while PostgreSQL and Ollama have no published host ports. Four named volumes hold database, document Git/blob content, models and generated secrets. Existing native `.local/` data and backups remain available.

Inputs: user's explicit one-command Docker request; [base requirements](../../../../../Docs/base-requirements.md), including PostgreSQL/Git/blob persistence; [US2.2.7: consistent database/Git updates](../../../../../Docs/documents/backlog-descriptions/E2%20Жизненный%20цикл%20и%20Версионность%20(Lifecycle%20%26%20Versioning)/F2.2%20Контроль%20версий%20(Git%20Integration)/US2.2.7%20Обеспечение%20транзакционности%20обновления%20БД%20и%20Git/description.md). Source documents are in the persistent sibling `../Docs` checkout. One-command orchestration is this operational addition, not a claim of an existing backlog feature.

## Вне скоупа

Public production deployment, CI redesign, cluster orchestration, automatic backups, deletion of the native installation, new models/prompts/retrieval rules, or claiming Docker CPU inference on macOS has native Metal performance.
