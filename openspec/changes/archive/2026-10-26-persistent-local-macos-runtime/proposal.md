## Why

The application and local RAG currently depend on temporary tooling, process sessions and test storage. The user needs a persistent installation on this Mac that they can build, start, stop and inspect themselves while keeping the existing demonstration articles, versions and attachments.

Feature ID: `persistent-local-runtime` (new entry to register when the verified change is archived).

## What Changes

- Provide native macOS setup and shell activation for Java 21, Maven, Node 24, PostgreSQL 18 with pgvector and local Ollama.
- Add project commands for non-destructive `setup`, `build`, `start`, `stop`, `status` and `logs`; place persistent data, configuration, models and process records under ignored `.local/`.
- Keep PostgreSQL, the application and Ollama on loopback; generate owner-readable local secrets, disable external AI/SMTP and Ollama cloud features.
- Transfer the current demonstration corpus as a consistent PostgreSQL/Git/blob snapshot, retain the source for rollback and verify documents, version references and RAG after stop/start.
- Document the exact daily workflow and verification evidence. No application API or Java business behavior changes.

## Capabilities

### New Capabilities

- `local-runtime`: Persistent native macOS lifecycle, safe local configuration, consistent data migration and reproducible operator instructions.

### Modified Capabilities

None. Existing content, export, authentication and RAG contracts remain unchanged.

## Impact

Changes are limited to `scripts/local/`, ignore rules, local operator documentation and OpenSpec. Installed tools and runtime files live outside tracked application sources. Default local ports are application `18081`, PostgreSQL `55434` and Ollama `11434`; database `knowledge_base_rag`, application role `kb_user`.

Inputs: the user's explicit local-install request; [base requirements](../../../../../Docs/base-requirements.md) (PostgreSQL, Git and blob storage); [US2.2.7: consistent database/Git updates](../../../../../Docs/documents/backlog-descriptions/E2%20Жизненный%20цикл%20и%20Версионность%20(Lifecycle%20%26%20Versioning)/F2.2%20Контроль%20версий%20(Git%20Integration)/US2.2.7%20Обеспечение%20транзакционности%20обновления%20БД%20и%20Git/description.md); [US1.3.1: attachment storage](../../../../../Docs/documents/backlog-descriptions/E1%20Управление%20контентом%20(Content%20Management)/F1.3%20Вложения/US1.3.1%20Реализация%20сущности%20Attachment/description.md). These sources require preserving related storage; native macOS orchestration is this change's operational addition, not an existing backlog claim. Source documents were read from the checked-out Docs repository at `/private/tmp/trpo-tooling/Docs`.

## Вне скоупа

Automatic login/startup services, Docker/CI redesign, public or LAN deployment, remote credentials, production SLA, scheduled backups, new RAG models/prompts and a new model-quality evaluation campaign.
