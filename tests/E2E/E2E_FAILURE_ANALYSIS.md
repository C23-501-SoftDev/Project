# E2E failure analysis — 2026-10-25

The publication lifecycle, diff representation, login heading, history/search UI and test fixtures now reflect the current application contract. Test data uses generated IDs, explicit publication and unique spaces. Backend fixture retries were removed; browser retries remain disabled.

## Remaining application defect: admin space filters

Affected scenarios: C5; SPACES-FILT status deleted, status all, owner filter, status active + owner, status deleted + owner; SPACES-UI-04.

`admin-spaces.html` does not bind `statusFilterWrapper` to `statusFilter`. Selecting a displayed status leaves the request at `active`. `loadAdminSpaceOwners()` replaces options after `initCustomSelects()` has attached handlers to the old nodes, so subsequent owner selection does not change the request either. These are application bugs, not expected limitations. Keep assertions and repair the controls in a separate branch.

The first refreshed full run also contained C4's obsolete confirmation click. C4 now creates its own space and checks the actual DELETE response. Focused validation results are recorded with the OpenSpec change.

## Historical results

The original suite on the same environment had 122 passing and 22 failing browser scenarios. Earlier 51/57-test counts and WIP expectations in this file were obsolete.
