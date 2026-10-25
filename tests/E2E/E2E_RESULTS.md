# Regression results — 2026-10-25

Environment: macOS arm64, Java 21.0.7, Maven 3.9.9, PostgreSQL 18.6, Chromium (Playwright 1.59.1). AI and email delivery disabled. The test database is isolated from application data.

## Reproduction

From `backend`: `mvn clean verify`.
From repository root: `node --test backend/src/test/javascript/*.test.js`.
From `tests/E2E`: `npm ci`, `npx playwright install chromium`, then `E2E_BASE_URL=http://127.0.0.1:18080 npm test` against a running application with migrations applied.

## repair-test-suite

- Java: **110 passed, 0 failed, 0 skipped**; executable JAR built.
- JavaScript history renderer: **1 test file passed** (contains multiple assertions).
- Full browser suite: **136 passed, 8 failed, 0 skipped, 0 retries** out of 144.
- The C4 failure was an obsolete confirmation expectation; the action deletes the test's own space immediately. Final focused verification: **3 passed** (C4 and independently seeded create/cancel pages).
- Seven remaining browser failures expose existing admin-space filter defects: the status select has no hidden-input target, and dynamically replaced owner options lose click handlers. They are retained as failing regression coverage for the next, separate application fix.

These results do not claim that all browser tests pass. See `E2E_FAILURE_ANALYSIS.md` for the remaining defect and its scope.
