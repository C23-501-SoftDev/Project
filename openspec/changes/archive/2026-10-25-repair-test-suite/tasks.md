## 1. Restore reliable regression coverage
- [x] 1.1 Update Java publication fixtures, migration reference and servlet error tests.
- [x] 1.2 Make browser fixtures independent and update obsolete UI expectations.
- [x] 1.3 Проверка критериев качества: [quality gates](../../../quality-gates.md), full Java/JavaScript/Playwright suites; record results.

## Validation evidence

2026-10-25: `mvn clean verify` — 110 passed, 0 failures/errors/skips; JAR built. `node --test backend/src/test/javascript/*.test.js` — passed. Full Playwright run — 136 passed / 8 failed / 0 skipped (144 total). After correcting C4's obsolete confirmation and independent create/cancel fixtures, focused run — 3 passed. Seven pre-existing admin-space filter failures remain visible and will be addressed in a separate application change. See `tests/E2E/E2E_RESULTS.md` and `E2E_FAILURE_ANALYSIS.md`.
