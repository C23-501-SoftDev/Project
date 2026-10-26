## ADDED Requirements

### Requirement: Persistent non-destructive local setup
The project SHALL provide native macOS `setup` and shell activation commands for the required Java, Maven, Node, PostgreSQL/pgvector and Ollama tools. Runtime configuration, database files, document Git/blob storage, model files, logs and process records SHALL reside in an ignored persistent `.local/` directory. Repeating setup MUST preserve existing user data and generated secrets.

#### Scenario: First setup prepares persistent state
- **WHEN** an operator runs setup with the supported native tools installed and no existing runtime state
- **THEN** the project prepares the required directories and local configuration, including a random database password and JWT secret readable only by the owner
- **AND** setup leaves its managed PostgreSQL running for database/extension provisioning or an operator-controlled restore, without starting Ollama or the application
- **AND** missing prerequisites are reported with an actionable error instead of silently using temporary test storage

#### Scenario: Repeated setup preserves edits
- **WHEN** setup is run again after documents and configuration already exist
- **THEN** existing database contents, Git history, blobs and secret values remain unchanged
- **AND** setup does not restore the demonstration snapshot over the operator's data

### Requirement: Explicit managed lifecycle
The project SHALL provide `build`, `start`, `stop`, `status` and `logs` commands. Start SHALL use the local configuration, check PostgreSQL and pgvector, required local models and application readiness, and report a failure when a dependency cannot become ready. Stop SHALL terminate only processes belonging to this runtime without deleting persistent data. No automatic startup at login SHALL be configured by this change.

#### Scenario: Operator starts a ready stack
- **WHEN** the operator builds the application and starts the prepared stack
- **THEN** PostgreSQL, Ollama and the application become ready in dependency order
- **AND** status identifies the managed services and the application is reachable at its documented local address

#### Scenario: Existing listener is unrelated
- **WHEN** an expected local port is occupied by a process not identified as belonging to this runtime
- **THEN** start reports a conflict and does not terminate or silently adopt that process

#### Scenario: Stop and start preserve documents
- **WHEN** the operator stops a running stack and subsequently starts it again
- **THEN** the same documents, published versions and stored attachments remain readable
- **AND** restarting does not require the original terminal or an active coding-agent session

### Requirement: Local-only service configuration
The installed runtime SHALL bind PostgreSQL, the application and Ollama to loopback. The default ports SHALL be `55434`, `18081` and `11434` respectively. External text-rewrite AI and SMTP delivery SHALL be disabled, and Ollama cloud features SHALL be disabled. Runtime secrets MUST remain outside tracked files and status output.

#### Scenario: Local RAG is enabled without external services
- **WHEN** the prepared runtime starts with its documented defaults
- **THEN** RAG uses local `qwen3-embedding:0.6b` with 1024 dimensions and local `qwen3:4b`, with the required `vector` extension and `rag` migrations available
- **AND** the services do not listen on a non-loopback network interface, `AI_ENABLED=false`, `NOTIFICATIONS_ENABLED=false` and `OLLAMA_NO_CLOUD=1`

### Requirement: Consistent migration of the existing corpus
The transition from the temporary demo SHALL preserve its PostgreSQL metadata, document Git history and blob files as one consistent set, including the existing 15 demonstration articles and their published version references. Migration SHALL quiesce source writers, retain the source or snapshot for rollback, and verify the destination before declaring success. Ordinary setup/start MUST NOT automatically overwrite existing destination data.

#### Scenario: Existing demo is moved into permanent storage
- **WHEN** the operator performs the one-time migration with source writers stopped
- **THEN** document identifiers, published version SHAs, relevant database row counts and attachment content match the captured source set
- **AND** saved article content and source-linked local RAG answers remain accessible after destination stop/start

#### Scenario: Migration verification fails
- **WHEN** the restored metadata refers to missing Git versions or attachment files, or corpus identity does not match
- **THEN** the migration is not reported as complete and the retained source/snapshot remains available for recovery

### Requirement: Reproducible local operator instructions and evidence
The repository SHALL document prerequisites, actual setup/build/start/stop/status/log commands, addresses, persistent paths, configuration handling, troubleshooting and limitations. Verification SHALL include the repository's full required regression suite on isolated test data, non-destructive lifecycle checks, and at least one real local RAG smoke request with valid source references. Unchanged model prompts and retrieval rules SHALL NOT be presented as newly evaluated quality based only on the smoke request.

#### Scenario: User follows the documented daily workflow
- **WHEN** a user starts from the checked-out repository in a normal macOS shell
- **THEN** the documented commands are sufficient to activate tools, build and operate the local stack without temporary agent tooling
- **AND** the report distinguishes verified persistent-runtime behavior from historical RAG quality evaluations and untested production deployment
