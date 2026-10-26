# Verification: Persistent native macOS runtime (2026-10-26)

Maven clean verify: 281 tests, 0 failures/errors/skips, including 17 real PostgreSQL/pgvector tests; the public `scripts/local/stack.sh build` command also passed all 281 with 17 PostgreSQL checks and atomically installed the JAR. Node: 12 passed. Python RAG runner: 7 passed; native runtime checks: 12 passed; both confirmed on installed Python 3.15.1. Playwright: 158 passed, 0 failed/flaky/skipped, workers=1, retries=0, 1.1 minutes, fresh isolated database `knowledge_base_local_setup_test` on the test PostgreSQL port 55433 and app port 18080. Persistent user data on port 55434 was not the test target. Strict OpenSpec via the persistent wrapper: 37/37.

Installed tools: Oracle JDK 21.0.7, Maven 3.10.0, Node 24.22.1, PostgreSQL 18.6 / pgvector 0.8.7, Ollama 0.40.11, OpenSpec 1.2.0 and Playwright 1.59.1 with persistent Chromium files. No Java/API/model prompt or retrieval changes.

Migration fingerprints matched all 15 document rows, 15 versions, 2 spaces, 1 user and 0 attachments; all 15 Git SHA:path references and `git fsck` verified. Index: 15 DONE. Repeated setup preserved config SHA; a foreign Ollama listener was rejected without termination, then explicitly stopped before the managed stack started. Repeated start retained existing PIDs. Managed stop terminated only the app/Ollama/PostgreSQL belonging to this stack; the unrelated source PostgreSQL and E2E application remained alive. Restart created new managed PIDs and restored healthy loopback listeners for all three services, unchanged config SHA and exact corpus fingerprints. Source/destination model digests matched.

Real admin login, article/history reads and local RAG passed before and after restart: backup/log retention 14/7 days with citations to documents 9/14 and checked version SHAs. The user stack is left running. There were no attachment bytes in this demo corpus, and this smoke is not a new model-quality evaluation. [Paths, migration evidence and workflow](../../docs/LOCAL_SETUP_RU.md).

# Verification: Cross-article RAG synthesis (2026-10-26)

Maven clean verify: 281 tests, 0 failures/errors/skips, including 17 real PostgreSQL/pgvector tests. Node: 12 passed. Python evaluation-runner checks: 7 passed. Playwright: 158 passed, 0 failed/flaky/skipped, workers=1, retries=0, fresh isolated PostgreSQL schema, 1.1 minutes. Tested application source: `b9d97fad352fe069bf2b4d377771c23d94fc50d0`. Strict OpenSpec after archive: 36/36.

New Java checks exercise the per-document cap before the global limit, deterministic ties, eight article sources, character budgets and provider contracts. Browser coverage verifies multiple source links with version/chunk provenance and rejection of external citation URLs. Real-model quality is measured separately in the [RAG reports](../rag/results/README.md).

Real Ollama acceptance: 40/40 HTTP 200; all 10 cross-article cases pass required facts and citations (baseline 7/10 due to missing secondary citations), original 24/24 answers and 6/6 refusals retained. No invalid citations or injection marker; manual review completed. Full ask p50=2458ms, p95=4146ms; synthetic local scope only. Candidate criteria v4 additionally enforce original single-source answer/citation rates separately, preventing new cases from hiding old regressions; baseline raw v3 evidence remains unchanged.

# Verification: Portable attachments and images in exports (2026-10-26)

Maven clean verify: 272 tests, 0 failures/errors/skips, including 14 real PostgreSQL/pgvector tests. Node: 12 passed. Playwright: 158 passed, 0 failed/flaky/skipped, workers=1, retries=0, fresh isolated PostgreSQL schema, 1.1 minutes. Strict OpenSpec: 36/36. Branch includes the previously merged tree optimization.

Four new browser scenarios verify UI downloads, offline ZIP images/file links, actual standalone HTML attachment download, PDF/DOCX output, foreign-attachment isolation and controlled errors. Java checks cover actual PDF image pixels, DOCX drawing relationships/positions/aspect ratio, filesystem confinement, limits and access revocation during rendering. Rendered PDF visually inspected. [Export contract](../../docs/EXPORTS.md).

# Verification: Document-view load acceptance and efficient tree renderer (2026-10-26)

Maven clean verify: 251 tests, 0 failures/errors/skips, including 14 real PostgreSQL/pgvector tests. Node: 12 passed (3 application + 9 performance harness). Playwright: 154 passed, 0 failed/flaky/skipped, workers=1, retries=0, fresh isolated PostgreSQL schema, about one minute. Strict OpenSpec: 35/35.

Both 25-reader acceptance profiles and separate 50-reader stress passed after replacing per-node Thymeleaf processing; all original failed measurements remain in [performance evidence](../performance/results/README.md).

# Verification: Local authorized RAG through Ollama and pgvector (2026-10-26)

Maven clean verify: 246 tests, 0 failures/errors/skips, including 14 real PostgreSQL/pgvector tests and 6 Spring/MockMvc RAG authorization tests. Node: 3 passed. Playwright: 154 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh isolated schema, 68.5 seconds. Final JAR used by both local test applications.

Real-model evaluation: 30/30 successful requests, 24/24 expected documents retrieved and cited with substantive answers, 6/6 unsupported questions declined, no invalid citations or injection canary. Full ask p50=1720ms, p95=2023ms; synthetic local scope only. [Methodology, all three runs and limitations](../rag/results/README.md).

# Verification: Verified architecture, specifications and remaining roadmap (2026-10-25)

Maven clean verify: 127 tests, 0 failures/errors/skips. Node tests passed. Playwright: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.

# Verification: Enforced CSRF for stateless cookie authentication and frontend mutations (2026-10-25)

Maven clean verify: 127 tests, 0 failures/errors/skips. Node tests passed. Playwright: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.

# Verification: Authorized asynchronous document trees and measured page performance (issue #23, US4.4.1) (2026-10-25)

Maven clean verify: 124 tests, 0 failures/errors/skips. Node tests passed. Playwright: 148 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.

# Verification: Independent sidebar tree and search result scrolling (issue #20) (2026-10-25)

Maven clean verify: 121 tests, 0 failures/errors/skips. Node tests passed. Playwright: 145 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.

# Verification: Editable DOCX tables, headings and nested lists (US5.1.3) (2026-10-25)

Maven clean verify: 121 tests, 0 failures/errors/skips. Node tests passed. Playwright: 144 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.

# Verification: Portable Cyrillic PDF fonts (US5.1.2) (2026-10-25)

Maven clean verify: 118 tests, 0 failures/errors/skips. Node tests passed. Playwright: 144 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.

# Regression results — 2026-10-25

Environment: macOS arm64, Java 21.0.7, Maven 3.9.9, PostgreSQL 18.6, Chromium (Playwright 1.59.1). AI and email delivery disabled. The test database is isolated from application data.

## Reproduction

From `backend`: `mvn clean verify`.
From repository root: `node --test backend/src/test/javascript/*.test.js`.
From `tests/E2E`: `npm ci`, `npx playwright install chromium`, then `E2E_BASE_URL=http://127.0.0.1:18080 npm test` against a running application with migrations applied.

## Latest: sanitize-markdown-html

Java **117 passed**, JavaScript test passed, full Playwright **144 passed / 0 failed / 0 skipped / 0 retries**. New browser regression checks script/event-handler removal in document view, editor preview and standalone HTML. PDF resource test observed zero HTTP requests.

## Previous: protect-document-exports

Java **114 passed**, JavaScript test passed, full Playwright **144 passed / 0 failed / 0 skipped / 0 retries** on a fresh PostgreSQL schema. Draft export, version and attachment access regressions are covered by the added Java integration tests.

## Previous: repair-admin-space-filters

- Java: **110 passed**, no failures/errors/skips; executable JAR built.
- JavaScript: **1 test file passed**.
- Full Playwright: **144 passed, 0 failed, 0 skipped, 0 retries**, 60.7 seconds, fresh PostgreSQL test schema.
- The seven admin-filter regressions from the test-repair branch are fixed.

## repair-test-suite

- Java: **110 passed, 0 failed, 0 skipped**; executable JAR built.
- JavaScript history renderer: **1 test file passed** (contains multiple assertions).
- Full browser suite: **136 passed, 8 failed, 0 skipped, 0 retries** out of 144.
- The C4 failure was an obsolete confirmation expectation; the action deletes the test's own space immediately. Final focused verification: **3 passed** (C4 and independently seeded create/cancel pages).
- Seven remaining browser failures expose existing admin-space filter defects: the status select has no hidden-input target, and dynamically replaced owner options lose click handlers. They are retained as failing regression coverage for the next, separate application fix.

The test-repair results above are historical. The latest complete suite passes after the separate admin-filter fix.
