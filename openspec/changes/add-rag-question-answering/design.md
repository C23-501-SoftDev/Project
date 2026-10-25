## Context
Documents already have SQL metadata, publication state, permissions and Git versions. The current external AI client only transforms text. RAG is not implemented.
## Goals / Non-Goals
Answer questions from published articles the caller can read, with citations and explicit insufficient-evidence responses. Initial scope excludes OCR, arbitrary attachment ingestion, web search and autonomous document edits.
## Proposed Decisions
- Controller: POST /api/ai/ask accepts `{ question, spaceIds? }`; response `{ answer, sources, insufficientEvidence, requestId }`. Each source has documentId, title, URL, version SHA and chunk locator. JWT/CSRF and rate limits apply. Proposed validation: question 1–2000 characters, at most 20 selected spaces. Exact limits can be tuned before implementation.
- Service: RagAnswerService orchestrates retrieval, permission checks, prompt construction, generation and final source validation. Provider ports isolate embedding and generation implementations; the existing rewrite service remains separate.
- Repository/DB: proposed PostgreSQL + [pgvector](https://github.com/pgvector/pgvector), which supports vector similarity search in PostgreSQL. This choice reuses the existing database; no extension is installed by this change. Pin an extension version after deployment compatibility is verified. A separate vector service is an alternative if operational scale requires it.
- Store chunks with documentId, spaceId, version/content hash, chunk number, text, embedding model/version and indexing state. Dimension follows the selected embedding model. Use idempotent upserts keyed by source version/chunk/model.
- Index after publication or update commits through a durable job/outbox. Rebuild embeddings only for changed content/model; deletion invalidates retrieval immediately through authoritative metadata filtering, then removes index rows asynchronously.
- Retrieve within authorized published documents, then re-check SQL permissions/state before calling the model. Do not rely solely on stale permissions copied into the vector index. Revalidate all sources before returning an answer; if access changes, discard the answer and request retry.
- Treat retrieved text as quoted reference material, never instructions. The server constructs source links from verified document IDs; the model cannot invent arbitrary citation URLs.
- AI stays disabled by default. Configure request timeouts, bounded context/output, per-user limits and cost caps before enabling. Log request IDs, timing, model and source IDs; avoid raw questions/documents in routine logs.
## Risks / Trade-offs
Approximate vector search can reduce recall when filters remove candidates; test oversampling/exact fallback against a labelled set. Generation can hallucinate; require evidence and provide an insufficient-evidence path. External providers receive authorized document excerpts, so choose the provider and data policy deliberately before implementation.
## Acceptance Plan
Prepare at least 30 labelled Russian questions covering multiple spaces, answerable and unanswerable cases. Proposed initial gate: expected source in top 5 for at least 90% of answerable queries, zero unauthorized source/answer leakage, all returned citations resolve to the supplied versions, and failures/timeouts produce a controlled response. Set latency/cost gates after provider selection and measured baseline, not from unsupported estimates.
## Open Questions
Embedding model and dimension; generation provider/model; approved external-data policy; request/cost limits; extension deployment permissions; evaluation dataset ownership.
## Migration / Rollback
Future implementation adds an opt-in extension/table/index migration and durable indexing jobs. Enable only after backfill and ACL tests. Disable feature flag to roll back serving; preserve original document data. Indexes can be rebuilt from Git/SQL metadata.
