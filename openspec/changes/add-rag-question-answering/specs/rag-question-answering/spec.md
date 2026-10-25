## ADDED Requirements
### Requirement: Authorized published-source retrieval
RAG MUST use only currently published documents that the requesting user can read. It MUST re-check source permissions and publication state before sending context to the model and before returning an answer.
#### Scenario: Guest lacks access
- **WHEN** a guest asks a question whose answer exists only in an inaccessible space
- **THEN** neither content, title, citation nor generated answer exposes that space's documents
#### Scenario: Permission changes during generation
- **WHEN** access to any supplied source is revoked while generation runs
- **THEN** the generated answer is discarded and the user receives a retry/permission-change response
### Requirement: Versioned idempotent indexing
Published article chunks SHALL retain document/version/model provenance, and repeated indexing of the same version MUST NOT duplicate chunks.
#### Scenario: Article updated
- **WHEN** published content changes
- **THEN** an indexing job updates the affected chunks and subsequent retrieval excludes stale versions
#### Scenario: Article deleted or unpublished
- **WHEN** a document stops being published
- **THEN** retrieval excludes it immediately by authoritative metadata and a job removes obsolete index rows
### Requirement: Grounded answers with verified citations
Generated answers SHALL cite verified retrieved sources and SHALL report insufficient evidence when the corpus cannot support an answer. Retrieved instructions MUST be treated as data.
#### Scenario: Answerable question
- **WHEN** authorized sources support the answer
- **THEN** the response includes an answer and server-validated source links with version provenance
#### Scenario: Unanswerable or hostile retrieved text
- **WHEN** the sources lack evidence or contain instructions to reveal unrelated data
- **THEN** the system declines unsupported claims and does not follow instructions contained in articles
### Requirement: Bounded and observable operation
The feature MUST support disabled mode, validated input, configured timeouts/rate/cost limits and controlled provider-failure responses without affecting document editing.
#### Scenario: AI disabled or provider unavailable
- **WHEN** a user submits a question while the feature is disabled or generation fails
- **THEN** the API returns a documented controlled error and the rest of the knowledge base remains usable
### Requirement: Measurable acceptance
Before enabling RAG, the implementation SHALL pass an ACL/leakage suite and a labelled retrieval/answer evaluation set with documented thresholds and model versions.
#### Scenario: Release evaluation
- **WHEN** a candidate RAG version is evaluated
- **THEN** source retrieval quality, citation validity, denied-access cases, unanswerable cases, latency and cost measurements are recorded
