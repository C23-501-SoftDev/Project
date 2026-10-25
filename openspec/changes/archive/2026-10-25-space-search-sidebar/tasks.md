- [x] 1. Обновить `SpaceService` и `SpaceController` для поиска пространств по названию.
- [x] 2. Обновить sidebar на SSR-страницах: поле поиска, список результатов, состояние без результатов.
- [x] 3. Добавить интеграционный тест для поиска пространств.
- [x] 4. Проверка качества: `mvn clean compile`, `mvn test`.
- [x] 5. Обновить `BACKTRACKER.md` с новым API-эндпоинтом.
## Reconciliation evidence (2026-10-25)
Implementation and HTTP contracts were checked against current source and BACKTRACKER.md. Maven clean verify: 127 tests, 0 failures/errors/skips; Node: 3 passed; full Playwright on a fresh PostgreSQL 18.6 test schema: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0. Health endpoint: UP. See docs/PROJECT_REVIEW_RU.md and tests/E2E/E2E_RESULTS.md. This records verification of existing implementation, not a claim that it was first implemented today.
