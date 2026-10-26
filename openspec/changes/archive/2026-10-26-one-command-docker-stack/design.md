## Context

The native Mac stack is verified and persistent, but the user now requests one Docker command for the complete application. The old Compose/onboarding path assumes host environment files and SSH/Git setup and omits the local RAG model lifecycle. PostgreSQL metadata, document Git and blobs form one logical data set; changing the runtime must preserve those relationships and the complete current user state, including additions made after the earlier demonstration corpus was prepared.

## Goals / Non-Goals

**Goals:** `docker compose up -d --build` from a checkout without `.env`; explicit health-aware wait; persistent isolated storage/secrets/models; non-root app image; repeatable cold/warm starts; safe migration of the current corpus; accurate instructions and real Docker evidence.

**Non-Goals:** application API/Java behavior changes, production exposure/SLA, Kubernetes, CI redesign, new models/prompts/retrieval logic, automatic backup/migration on every start, or removal of the native workflow.

## Decisions

1. **Compose owns the complete runtime.** The default project contains an initialization job, PostgreSQL, Ollama, a model-preparation job and the application. Initialization completes before its dependants; model preparation waits for healthy Ollama; app startup waits for healthy PostgreSQL/Ollama and successful model preparation. Docker's health-aware form is `docker compose up -d --build --wait --wait-timeout 1800`. A timeout/failure is not reported as readiness. Native services are not prerequisites.
2. **Package the app, not the host checkout.** Use a multi-stage Maven/JDK 21 build and JRE 21 runtime, with the app running as UID 1000. Runtime mounts are named data volumes, not host source, `.ssh`, `.gitconfig` or credentials. The document Git repository contains user content and is independent of the source repository; automatic source-repo clone/push setup is removed from the default path. Controller/service/repository and application schema definitions do not change. Existing Liquibase migrations own the database schema; provisioning the `vector` extension and secret files is runtime work.
3. **Use four persistent volumes.** Separate volumes hold PostgreSQL 18 data, document content (Git and blobs), Ollama models and generated secrets. Initialization creates missing random database/JWT secrets once and prepares volume permissions without replacing existing content. Entry points consume those files; logs and tracked `.env.example` contain no generated secrets. Ordinary `down` keeps volumes. Only an explicit documented `down -v` resets the selected Compose project's state.
4. **Keep services local and bounded.** Publish only `127.0.0.1:${APP_PORT:-8080}` for the app. PostgreSQL and Ollama remain reachable only inside the Compose network. Enable local RAG and `rag` migrations, disable external text-rewrite AI/SMTP, set `OLLAMA_NO_CLOUD=1`, retain `qwen3-embedding:0.6b` (1024) and `qwen3:4b`, and allow the configured RAG call timeout of 120 seconds. PostgreSQL 18 with pgvector 0.8.7 and Ollama 0.40.11 must be available for the ARM64 target. Default model setup pulls missing models, reuses persistent ones and verifies a real 1024-dimensional finite embedding plus a short chat call before completion; this technical readiness is not a model-answer quality evaluation.
5. **Use native ARM64 virtualization on this Mac.** Colima uses Virtualization.framework with 6 CPUs and 12 GiB RAM. Docker Desktop is an alternative engine, not another concurrently required runtime. Linux Ollama inside the Mac VM uses CPU here; it does not inherit native Metal acceleration. Report actual readiness/inference times without reusing native latency claims. Initial image/model downloads require Internet and disk space.
6. **Keep test and user projects distinct.** Full browser/integration checks use a separately named Compose project and its own volumes. Test helpers target only that project and never issue global volume deletion. Any test-only reduced model initialization must be explicit and must not weaken default full-stack readiness. Root additionally verifies the real default stack with actual Ollama and a two-source answer.

## Risks / Trade-offs

- [First startup downloads large images/models] → bounded readiness wait, visible init/model logs, reuse volumes on retry; retain incomplete-volume state rather than silently resetting user data.
- [CPU inference in the Mac VM is slower] → honest resource requirements and timeout configuration; preserve native Metal workflow as a documented alternative.
- [Secrets volume deleted independently of database] → document the four-volume set and do not promise automatic password recovery or overwrite existing database authentication.
- [Container ownership differs from native files] → prepare only target volume permissions and verify Git/blob readability as the non-root app; never change host source ownership.
- [Inconsistent migration] → stop native app writers, capture PostgreSQL/Git/blob together, restore only into the intended empty target and verify IDs, versions, files and model digests; retain native source/backup.
- [Outdated helper scripts still use fixed container names or destructive cleanup] → align Make/dev/E2E helpers with project-scoped Compose services and document the new defaults.

## Migration Plan

1. Install and start the Mac Docker engine and Compose/build tools; record actual versions/resources.
2. Validate Compose without `.env`, build images and verify cold startup on a fresh isolated project; run required regression tests in isolated databases/volumes.
3. Stop source writers, preserve a consistent native dump/Git/blob snapshot of all current data, prepare empty user Docker volumes and restore that snapshot rather than a historical demo dump. After obtaining the Ollama image, `docker compose --project-name trpo up --no-start --no-deps ollama` prepares its stopped container and correctly labelled model volume. Copy compatible model files once and verify digests; ordinary fresh-clone startup remains empty except application seed data.
4. Verify app health, login, saved article/history, exports and actual RAG with two checked citations. Repeat start and `down`/`up`; compare persistent corpus and secret identities against a quiescent baseline captured immediately before the lifecycle check. If the user has published or edited since migration, retain that newer state instead of treating a historical fingerprint mismatch as permission to revert it.
5. Keep the native data/backup available. On failure, stop the Docker project and resume the consistent native set. Never merge independently captured database/Git/blob snapshots or overwrite newer Docker edits with the old demo dump automatically.

## Open Questions

No product decision blocks implementation. Record actual image architecture, engine/tool versions, cold-start and inference outcomes after execution; service/volume names and optional test-only switches will be reflected in operator documentation from the final Compose file.
