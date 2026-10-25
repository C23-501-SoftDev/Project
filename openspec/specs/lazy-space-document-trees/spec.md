# lazy-space-document-trees Specification

## Purpose
Authorized asynchronous document trees and measured page performance (issue #23, US4.4.1). This specification records the implemented behavior and its verifiable acceptance criteria.

## Requirements
### Requirement: Independent page and tree loading
Initial document pages SHALL render accessible space controls without querying or embedding document trees for every space. Trees SHALL load asynchronously when required.
#### Scenario: Many closed spaces
- **WHEN** a document page opens with thousands of documents in other spaces
- **THEN** its initial HTML contains no document tree rows and document content can load independently
#### Scenario: Repeated expansion
- **WHEN** the same tree is expanded repeatedly within one page
- **THEN** at most one request is in flight and a successfully loaded tree is reused
#### Scenario: Failed tree request
- **WHEN** a tree request fails
- **THEN** an accessible retry control is displayed and retry can load the tree

### Requirement: Authorized ordered tree fragments
Tree fragments MUST enforce space READ access and SHALL show drafts only to their author or an administrator, exclude deleted documents, and preserve hierarchy/sibling order.
#### Scenario: Reader requests a tree
- **WHEN** a reader loads a space containing published documents and another author's draft
- **THEN** the published documents appear and the draft title does not appear
#### Scenario: Guest lacks space access
- **WHEN** a guest without space permission requests its tree
- **THEN** the server returns 403 without document metadata

### Requirement: Measured document viewing target
The change SHALL record measurements against the US4.4.1 two-second document-view target with dataset size and environment stated.
#### Scenario: Local performance verification
- **WHEN** the performance fixture is run before and after the change
- **THEN** readiness times, initial HTML size and sidebar node counts are recorded, with limitations explicitly stated

