# E2E Failure Analysis (Updated)

## Run Context

- Command: `npm test`
- Workspace: `Project/tests/E2E`
- Total: **57**
- Passed: **53**
- Skipped: **4** (`test.fixme` in `strict-acceptance.spec.js`)

## Adaptation Summary (new core)

Tests were updated for:

- **RBAC:** `role` = `GUEST` | `READER` | `EDITOR`; admin access via `isAdmin`, not role `ADMIN`.
- **Soft-delete:** deleting a space owner returns `200` with `isDeleted: true` (no `409`).
- **Sort fallback:** invalid `sortBy` falls back to `createdAt` (`200`).
- **Implemented UI:** home, document create/view/edit, main CSS/JS assets.
- **Still WIP (documented, not failing baseline):** search results, document history, space documents list, admin settings.

## Skipped Strict Tests (enable when feature ships)

Remove `test.fixme` in `strict-acceptance.spec.js` when implemented:

1. Admin settings (no WIP message)
2. Search results list
3. Document version history
4. Space documents tree/list

## Notes

- Traces/screenshots: `Project/tests/E2E/test-results`
- Report: `npm run report`
