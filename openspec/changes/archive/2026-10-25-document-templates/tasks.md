## Tasks

- [x] Update the template-based document creation flow to preserve template context and assign requirement numbers.
- [x] Add a persistent PostgreSQL counter table for requirement numbers keyed by space and template.
- [x] Cover the numbering flow with integration tests for same-space continuation and cross-space reset.
- [x] Проверка качества: выполнить quality gates из `openspec/quality-gates.md`.
## Reconciliation evidence (2026-10-25)
Implementation and HTTP contracts were checked against current source and BACKTRACKER.md. Maven clean verify: 127 tests, 0 failures/errors/skips; Node: 3 passed; full Playwright on a fresh PostgreSQL 18.6 test schema: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0. Health endpoint: UP. See docs/PROJECT_REVIEW_RU.md and tests/E2E/E2E_RESULTS.md. This records verification of existing implementation, not a claim that it was first implemented today.
