## 1. Align document read access
- [x] 1.1 Apply shared visibility guards and add export/version/attachment regression coverage.
- [x] 1.2 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы.
- [x] 1.3 Проверка критериев качества: [quality gates](../../../quality-gates.md); full Java, JavaScript and browser suites.

## Validation evidence
2026-10-25: `mvn clean verify` — **114 passed, 0 failures/errors/skips**, JAR built; JavaScript history test passed; full Playwright on fresh PostgreSQL 18.6 — **144 passed, 0 failed, 0 skipped, 0 retries**.
