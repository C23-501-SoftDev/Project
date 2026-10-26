## 1. Native tools and runtime configuration

- [x] 1.1 Install/verify Java 21, Maven, Node 24, PostgreSQL 18 with pgvector and Ollama; make the selected commands available through `scripts/local/env.sh`.
- [x] 1.2 Add ignored persistent `.local/` paths and non-destructive setup with owner-only generated JWT/database secrets and loopback defaults.
- [x] 1.3 Implement `scripts/local/stack.sh build/start/stop/status/logs` with dependency readiness, managed-process checks and actionable conflict/errors.

## 2. Existing corpus migration

- [x] 2.1 Record the current source corpus and paths; stop source writers and capture a consistent PostgreSQL/Git/blob snapshot while retaining rollback data.
- [x] 2.2 Restore the intended empty permanent database, Git/blob directories and local model store; verify document/version identifiers, SHA references, relevant counts and attachment content.
- [x] 2.3 Start the permanent stack with local RAG enabled and external AI/SMTP/cloud features disabled; verify actual loopback listeners and required models/extension.

## 3. Operator documentation

- [x] 3.1 Write `docs/LOCAL_SETUP_RU.md` with installed versions, shell activation, daily commands, paths, local addresses, configuration, troubleshooting and preservation/rollback limits.
- [x] 3.2 Link the persistent native workflow from the top of README and RAG documentation; distinguish it from older Docker and manual diagnostic instructions.

## 4. Verification and completion

- [x] 4.1 Проверка критериев качества: execute the full checks in [openspec/quality-gates.md](../../../quality-gates.md), including Maven, Node, Python, Playwright and strict OpenSpec validation on isolated test data; record actual totals.
- [x] 4.2 Check shell syntax and meaningful lifecycle regressions: repeated setup/start, port conflict handling and managed stop without data/secret replacement.
- [x] 4.3 Verify persistent stop/start with the migrated corpus, saved article/history/attachment references and a real local RAG answer with valid sources; record evidence without claiming a new full quality evaluation.
- [x] 4.4 Review the tracked diff for secrets/runtime data, record final setup/migration evidence, then synchronize the `local-runtime` main spec, register `persistent-local-runtime` and archive this change.

## Verification evidence — 2026-10-26

281 Java tests (17 real PostgreSQL/pgvector), 12 Node, 7 Python RAG checks, 158 Playwright checks passed without failures/skips; isolated test database `knowledge_base_local_setup_test`, not persistent user data. Twelve runtime checks and all seven RAG runner checks passed on installed Python 3.15.1. The public `stack.sh build` command also passed 281 Java tests including 17 PostgreSQL checks and installed the JAR atomically. The persistent OpenSpec wrapper validated all 37 active changes/specs strictly; `git diff --check` passed.

Migration matched full row fingerprints for 15 documents, 15 versions, 2 spaces, 1 user and 0 attachments; all 15 Git SHA:path references and `git fsck` verified. There were no attachment bytes in the source corpus to exercise. Setup preserved config SHA; foreign Ollama rejection and healthy managed startup verified. Repeated start retained PIDs; stop terminated only managed app/Ollama/PostgreSQL while unrelated source PostgreSQL and E2E app remained alive. Restart created new processes with all listeners on loopback and preserved config SHA and exact corpus fingerprints. Model digests matched the source. Login, article/history reads and real local RAG passed before and after restart: correct 14/7-day backup/log retention, documents 9/14 and verified version SHAs. User runtime remains running. Detailed versions and paths: [LOCAL_SETUP_RU.md](../../../../docs/LOCAL_SETUP_RU.md).
