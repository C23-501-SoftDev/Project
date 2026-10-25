## 1. Implementation
- [x] 1.1 Remove eager tree loading and add an authorized single-space fragment endpoint.
- [x] 1.2 Add asynchronous tree loading, request deduplication, retry and drag/drop compatibility.
- [x] 1.3 Add service/controller/browser regressions and measure the large-space fixture.
## 2. Verification
- [x] 2.1 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы.
- [x] 2.2 Проверка критериев качества: [quality gates](../../../quality-gates.md), Maven clean verify, Node tests, all Playwright tests.
- [x] 2.3 Update feature registry and archive/sync the specification.

Performance: see tests/performance/README.md and raw before/after observations. Median 741 → 65 ms, HTML 2,093,441 → 84,428 bytes; local single-user fixture only.

## Results (2026-10-25)
Maven clean verify: 124 tests, 0 failures/errors/skips. Node tests passed. Playwright: 148 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.
