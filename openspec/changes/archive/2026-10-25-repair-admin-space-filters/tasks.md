## 1. Repair controls
- [x] 1.1 Bind status and preserve dynamic option selection and searchable focus.
- [x] 1.2 Проверка критериев качества: [quality gates](../../../quality-gates.md); run all Java, JavaScript and browser tests and record results.

## Validation evidence
2026-10-25: `mvn clean verify` — 110 passed, 0 failures/errors/skips, JAR built. JavaScript history test passed. Full Playwright suite against PostgreSQL 18.6 and Chromium — **144 passed, 0 failed, 0 skipped, 0 retries** (60.7 seconds). Existing filter scenarios verify the repaired controls.
