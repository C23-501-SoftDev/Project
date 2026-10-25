## Why
The existing AI helper rewrites supplied text but cannot answer questions using knowledge-base articles. The user's additional feature requires a vector index, article retrieval and generated answers. Feature ID: `rag-question-answering`. Status: planned; this change contains requirements and a proposed design, not an implementation.
## Inputs
User feature brief dated 2026-10-25; [project backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json); existing document publication, permissions and AI configuration code.
## What Changes
- Index published document versions as chunks with embeddings and provenance.
- Retrieve only authorized content and produce grounded answers with source links.
- Handle updates/deletions, insufficient evidence, disabled AI and provider failures.
- Add a question UI, API contract, operational limits and acceptance dataset.
## Capabilities
### New Capabilities
- `rag-question-answering`: authorized retrieval and grounded question answering over published articles.
### Modified Capabilities
None at the planning stage.
## Impact
Proposed controller/service/repository/DB/UI additions are described in design.md. Embedding/generation provider, cost limits and external data policy remain decisions before implementation. No vectors, migrations or external model calls are added by this planning change.
