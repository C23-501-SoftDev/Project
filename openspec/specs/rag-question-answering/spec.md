# rag-question-answering Specification

## Purpose
Provide local, source-grounded answers over currently authorized published articles using Ollama and a versioned pgvector index, with explicit refusals, provenance, access rechecks and measurable rollout criteria.
## Requirements
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
Before enabling RAG, the implementation SHALL pass an ACL/leakage suite and a labelled retrieval/answer evaluation set with documented thresholds and model versions. The local release evaluation SHALL require at least 90% expected-source recall@5, at least 90% substantive answers with sources and at least 90% expected-source citations on answerable questions, refusals for all unsupported questions, and zero invalid citations or detected injection markers.
#### Scenario: Release evaluation
- **WHEN** a candidate RAG version is evaluated
- **THEN** source retrieval quality, citation validity, denied-access cases, unanswerable cases, latency and cost measurements are recorded

### Requirement: Local-only inference and controlled rollout
RAG MUST use local Ollama with cloud features disabled and MUST reject external endpoints and cloud model names. Indexing and serving SHALL have separate switches.
#### Scenario: Local model execution
- **WHEN** an article is indexed or a question is answered
- **THEN** embeddings and generation use the configured local models; article text is not sent to external AI APIs
#### Scenario: Initial backfill
- **WHEN** RAG is enabled with serving disabled
- **THEN** the durable worker can prepare the index while question requests receive a controlled disabled response
#### Scenario: First published article has no version row
- **WHEN** the RAG worker finds a published article without version metadata
- **THEN** it captures an immutable Git baseline under the document mutation lock and persists a real version before indexing
