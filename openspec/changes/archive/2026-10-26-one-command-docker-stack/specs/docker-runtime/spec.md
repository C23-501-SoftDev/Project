## ADDED Requirements

### Requirement: One-command complete local Docker startup
The repository SHALL start the application, PostgreSQL 18 with pgvector and local Ollama models with `docker compose up -d --build` from a checkout without requiring `.env`, host Java/Maven, source/SSH/Git-config mounts or running native database/model services. The default host application address SHALL be `http://127.0.0.1:8080`, with an optional `APP_PORT` override.

#### Scenario: Fresh checkout and empty volumes
- **WHEN** an operator with a ready Docker engine and Compose runs the documented command without `.env`
- **THEN** the stack creates its initial state, builds the application, obtains the configured local models and starts the app using its normal migrations and seed account
- **AND** application source and user SSH/Git credentials are not mounted into running services

### Requirement: Dependency and model readiness
Initialization SHALL complete before consumers of generated secrets/data start. Application startup SHALL depend on healthy PostgreSQL and Ollama plus successful preparation of `qwen3-embedding:0.6b` and `qwen3:4b`, including a real 1024-dimensional finite embedding response and a successful short generation request. The documented `--wait --wait-timeout 1800` form SHALL report failure when required initialization or health does not complete, rather than treating a running container as sufficient readiness.

#### Scenario: Initial model download
- **WHEN** the model volume does not contain the required models
- **THEN** model preparation obtains them through Ollama and the app does not become ready through the normal startup path before model preparation succeeds

#### Scenario: Failed dependency or unavailable model
- **WHEN** initialization, PostgreSQL, Ollama or required model preparation fails
- **THEN** the dependent app is not reported healthy and the operator can inspect the responsible service logs

### Requirement: Persistent data and generated secrets
The stack SHALL keep database data, document Git/blob content, models and generated secrets in four named persistent volumes. Random database and JWT secrets SHALL be generated only when missing and MUST NOT be committed, printed by setup or replaced by repeated startup. Ordinary Compose `down`/`up` MUST preserve the four-volume set and user content.

#### Scenario: Repeat and restart preserve the corpus
- **WHEN** the operator repeats startup or performs ordinary `docker compose down` followed by startup
- **THEN** the database, document history, blobs, models and secret identities are preserved and published articles remain readable

#### Scenario: Explicit destructive reset
- **WHEN** an operator deliberately invokes the documented `down -v` reset for a selected project
- **THEN** the documentation identifies that its database, content, models and secrets are deleted
- **AND** no ordinary startup/test helper performs a global Docker volume cleanup or that reset on the user's project

### Requirement: Isolated local container services
Only the application port SHALL be published to host loopback by default. PostgreSQL and Ollama SHALL have no host-published ports. The application SHALL run as a non-root user in a Java 21 runtime image. Ollama cloud features, external text-rewrite AI and SMTP SHALL be disabled; RAG SHALL use the local Ollama service with 1024-dimensional embeddings and a bounded configured timeout.

#### Scenario: Inspect a running default stack
- **WHEN** the default stack is ready
- **THEN** the host can reach the app on loopback, PostgreSQL/Ollama are private to the Compose network, the app runs as UID 1000 and no host SSH/Git configuration is mounted
- **AND** local RAG is enabled, `OLLAMA_NO_CLOUD=1`, `AI_ENABLED=false` and `NOTIFICATIONS_ENABLED=false`

### Requirement: Controlled migration of current native user data
The one-time migration on the current Mac SHALL preserve the complete native dataset captured after stopping source writers as a consistent PostgreSQL/Git/blob set, including recent user-created documents, attachments and published version references. It MUST NOT replace that snapshot with an older demonstration fixture. It SHALL retain a recoverable native source/backup and verify the destination before declaring success. Fresh-clone startup MUST NOT depend on, embed or automatically restore that user's data.

#### Scenario: Migrate and restart existing user data
- **WHEN** source writers are stopped and the consistent set is restored into the intended empty Docker target
- **THEN** document/version identifiers, Git SHA:path references and attachment content match the captured source
- **AND** recent user additions are included; if the user edits the destination after initial verification, subsequent lifecycle comparisons use a new quiescent baseline and MUST NOT revert those edits to the earlier snapshot
- **AND** article/history/export and a real local RAG answer with two valid sources work after Docker down/up

### Requirement: Reproducible Docker verification and operator guidance
Documentation SHALL describe the one-command path, explicit readiness, status/logs, persistence/reset, engine startup, resource needs and native fallback. Acceptance SHALL include the full repository regression suite on isolated test data plus actual Docker cold/repeated start, down/up preservation, API/export and local model checks. Mac Docker inference SHALL be described as CPU execution for the verified VM, not native Metal performance or a new general quality/SLA result.

#### Scenario: Tests do not modify the user's volumes
- **WHEN** automated browser/integration checks create or remove fixtures
- **THEN** they use a separate Compose project/database/volume set and leave the user's corpus unchanged

#### Scenario: Report real Docker acceptance
- **WHEN** implementation is marked complete
- **THEN** the report records actual engine/image architecture, full test results, lifecycle/data preservation and a real source-linked RAG response
- **AND** distinguishes initial downloads, CPU generation latency and historical native quality results from the observed Docker run
