## Context
The current main branch has 23 unsuccessful Java tests. Most version fixtures use a retired PUT status field; navigation tests still expect placeholders.

## Goals / Non-Goals
Goals: retain meaningful assertions with deterministic isolated data and real publication requests.
Non-goals: weaken authorization, suppress tests, or implement missing product features in test helpers.

## Decisions
Change tests under `backend/src/test` and `tests/E2E`. Use POST publish before versioned saves, unique users and documents, and explicit servlet error-dispatch tests. Keep controller/service/repository/db production contracts unchanged. Validate against PostgreSQL 18 and Chromium in addition to H2.

## Risks / Trade-offs
Legacy E2E assertions can describe actual bugs or retired behavior. Compare each failure with the current contract; keep product regressions visible and address them in separate fixes.
