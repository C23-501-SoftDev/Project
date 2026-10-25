## Context
The live synthetic corpus has 15 short articles with one chunk each. Its immediate limitation is the single-fact prompt and five-result cutoff. Larger articles also compete for every slot because the SQL query previously had no per-document limit. Existing 24 answerable acceptance questions each expect one source.

## Goals / Non-Goals
**Goals:** combine evidence across articles, compare supported facts, show evidence gaps explicitly, preserve source provenance and local-only inference, and measure multi-article completeness independently.

**Non-Goals:** new models, cloud inference, web browsing, chat memory, query decomposition, attachment indexing or guaranteed exhaustive corpus coverage.

## Decisions
- `RagProperties` and `application.yml`: default `topK=8`, `maxChunksPerDocument=2`, independently validated bounds. Keep the existing 12,000-character context and initial 600-output-token / 8,192-context-token limits. Increasing top K alone cannot prevent a long article monopolizing the context.
- `RagIndexRepository` / `PgVectorRagIndexRepository`: rank eligible, threshold-qualified chunks per document by score and chunk index; apply per-document cap before the global score-sorted LIMIT. No new tables or migration. ACL allowlist, current publication/version/update checks and model/dimension filters remain authoritative.
- `RagAnswerService`: pass the configured cap and retain all pre/post-generation source rechecks and character budgeting. No controller or DTO shape changes.
- `OllamaRagClient`: instruct the model to combine sources, cite all used IDs, cover supported question aspects and explicitly state missing information. A useful partial answer has `insufficientEvidence=false`; a completely unsupported question still refuses. Contradictions must be disclosed rather than resolved by guessing. Preserve JSON field order `answer`, `sourceIds`, `insufficientEvidence` and untrusted-source handling.
- `tests/rag`: preserve the old 30-question dataset and add a separate expanded dataset. Expected document sets use ALL semantics for retrieval and citations. A separate multi-source completeness gate includes required factual groups; wording checks supplement manual inspection, not semantic proof. Preserve failed baseline results.

## Risks / Trade-offs
- More context can add irrelevant articles or increase latency → unchanged relevance threshold and hard budgets; run old refusal/injection cases and record actual latency.
- Per-article cap can omit details from a long article → configurable cap; document this bounded retrieval behavior. The new default prioritizes cross-article coverage.
- Generative synthesis can add unsupported causal claims → explicit prompt constraints, regression examples and manual review of real answers; citations alone are not proof of correctness.
- Keyword checks can accept an incorrect sentence → inspect stored real answers and keep the dataset small and reproducible.

## Migration Plan
Build and test on the task branch; run a dedicated local RAG acceptance stand with existing Ollama models. Merge directly to main after all repository quality gates and update the demo JAR while retaining its PostgreSQL/Git/blob data. Rollback uses the previous JAR and prior defaults. No reindex is required.
