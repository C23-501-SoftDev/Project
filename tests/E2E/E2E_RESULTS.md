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
