## Why

The test suite still assumes that PUT publishes a document and that completed pages are placeholders. This hides regressions behind fixture failures and prevents reliable validation of subsequent fixes.

Feature ID: `repair-test-suite`.
Requirements: [project backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json), document publication, version history, navigation and search.

## What Changes

- Build isolated, explicitly published fixtures for versioning and visibility tests.
- Test servlet error dispatch correctly and reference the current sort-order migration.
- Bring E2E fixtures and assertions into agreement with the implemented UI, retaining substantive coverage.
- Record reproducible full-suite validation against PostgreSQL.

## Capabilities

### New Capabilities
- `test-suite-contract`: repeatable tests using current publication and navigation contracts.

### Modified Capabilities
None.

## Impact

Backend tests, E2E tests and test documentation. Application behavior and production authorization are outside this change.
