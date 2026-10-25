## Why
The existing AI helper rewrites supplied text but cannot answer questions using knowledge-base articles. The user's additional feature requires a vector index, article retrieval and generated answers. Feature ID: `rag-question-answering`. Status: implemented and verified; the user approved local Ollama with no external document transfer on 2026-10-26.
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
None. Existing capabilities remain compatible; document row locks provide consistency for initial RAG version capture.
## Impact
Controller/service/repository/DB/UI additions and local model limits are described in design.md. The optional PostgreSQL migration requires pgvector; document writers acquire row locks for consistent initial version capture. Existing external AI rewriting is unchanged and independent.

## Out of scope
OCR, attachment ingestion, web search, autonomous edits, cloud models and production multi-node deployment.
