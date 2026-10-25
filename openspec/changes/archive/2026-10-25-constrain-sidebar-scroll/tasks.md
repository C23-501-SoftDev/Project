## 1. Implementation
- [x] 1.1 Constrain sidebar height and isolate tree/result scrolling.
- [x] 1.2 Add a browser regression for independent tree and main-content scrolling.
## 2. Verification
- [x] 2.1 Проверка критериев качества: [quality gates](../../../quality-gates.md), Maven clean verify, Node tests, all Playwright tests.
- [x] 2.2 Update feature registry and archive/sync the specification.

## Results (2026-10-25)
Maven clean verify: 121 tests, 0 failures/errors/skips. Node tests passed. Playwright: 145 passed, 0 failed/flaky/skipped, workers=1, retries=0, PostgreSQL 18.6 fresh test schema.
