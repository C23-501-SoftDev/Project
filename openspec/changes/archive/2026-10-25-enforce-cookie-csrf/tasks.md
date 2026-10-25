## 1. Implementation
- [x] 1.1 Enable server CSRF validation, correct frontend headers and configure Swagger.
- [x] 1.2 Update legitimate test clients and add real missing/invalid-token and header regressions.
## 2. Verification
- [x] 2.1 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы.
- [x] 2.2 Проверка критериев качества: [quality gates](../../../quality-gates.md), Maven clean verify, Node tests and all Playwright tests.
- [x] 2.3 Update feature registry and archive/sync the specification.

## Results (2026-10-25)
Maven clean verify: 127 tests, 0 failures/errors/skips. Node tests passed. Playwright: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.
