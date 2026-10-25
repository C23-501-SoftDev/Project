## Why
RAG answers individual facts but does not reliably combine related facts from several articles. The user confirmed this problem on 2026-10-26. The current prompt encourages a short direct fact from one source, retrieval has only five chunk slots, and the existing acceptance set expects one article per answer.

Feature ID: `rag-question-answering`.

## Inputs
User clarification; [existing project backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json); implemented [RAG specification](../../../specs/rag-question-answering/spec.md). Cross-article synthesis is an extension of the user-approved RAG feature, not a claim that the original backlog specified it.

## What Changes
- Enable grounded synthesis and comparison across retrieved articles, citing every source used.
- Return useful supported portions of a question and explicitly identify missing evidence; retain refusal when no useful part is supported.
- Expand the default retrieval budget to eight chunks and cap each article at two chunks before the global cutoff.
- Extend real local-model evaluation with questions requiring two or three articles and gates for all required citations and facts.

## Capabilities
### New Capabilities
None.
### Modified Capabilities
- `rag-question-answering`: multi-article evidence selection, synthesis and measurable cross-article acceptance.

## Impact
Application/service, domain repository port, pgvector query, Ollama prompt, configuration, tests and operational documentation. Existing request/response shape remains compatible; no database migration or model download is required. All inference remains local.

## Out of scope
Web search, cloud models, attachment ingestion, conversation history, autonomous query expansion and production quality guarantees.
