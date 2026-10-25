# document-view-load-acceptance Specification

## Purpose
Define the accepted 25-user document-view workload, preserve reproducible evidence and efficiently render authorized document trees.
## Requirements
### Requirement: Defined concurrent document-view acceptance
The implementation SHALL demonstrate document display within 2000ms for all observed browser navigations under the user-selected normal profile of 25 active authenticated users on the documented local acceptance environment. It MUST report the exact corpus, network conditions, workload, sample sizes and raw results.
#### Scenario: Distributed and large-space normal load
- **WHEN** the two normal profiles run for at least 60 seconds each against distributed documents and a 5000-document space
- **THEN** at least 20 browser navigations and 100 HTTP measurements per profile are collected, no errors occur, browser content-ready maximum is at most 2000ms, and endpoint/content-chain p95 is at most 2000ms

### Requirement: Reproducible isolated evidence
The benchmark MUST operate only against an explicitly authorized local test database, preserve prior reports and distinguish cold browser caches from cold servers.
#### Scenario: Unsafe target or incomplete run
- **WHEN** the target is not an opted-in test database or the required measurements cannot be completed
- **THEN** the runner refuses the target or reports a failed/incomplete run without presenting acceptance as passed
#### Scenario: Complete navigation workload and late failures
- **WHEN** workload version 2 measures a reader viewing a document
- **THEN** HTTP workers include all six defined endpoint groups including header create-permissions, the browser validates that response and drains tracked navigation requests before removing listeners, and late failures or bounded-drain timeouts fail the sample

### Requirement: Separate stress characterization
The benchmark SHALL measure a 50-user stress profile separately from the 25-user normal acceptance profile without silently changing acceptance thresholds.
#### Scenario: Stress limit reached
- **WHEN** stress latency or errors exceed normal thresholds
- **THEN** the report exposes those observations and retains the independently measured normal profile result

### Requirement: Efficient authorized tree rendering
The tree endpoint SHALL render the complete authorized hierarchy without per-node template expression processing, while preserving existing navigation and drag/drop behaviour.
#### Scenario: Tree markup and permissions
- **WHEN** a reader loads a hierarchy with nested nodes, a selected document and titles containing Unicode or HTML metacharacters
- **THEN** the response preserves hierarchy, order, selected styling and drag/drop attributes, escapes text and attributes, encodes title URL segments, returns UTF-8 HTML with no-store, and contains only nodes allowed by the existing visibility checks

