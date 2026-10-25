## Context
Source code and executed tests provide stronger implementation evidence than stale checkboxes. Documentation must distinguish actual features, measured local results and future requirements.
## Goals / Non-Goals
Make architecture, setup, capability status and next work reviewable. Do not implement RAG, mark unverified requirements complete or rewrite archived historical changes.
## Decisions
Documentation-only reconciliation: update config, registry and invalid active/root specs. Merge duplicate entries by featureId while retaining all referenced documents/endpoints. Reconcile five implemented legacy capabilities using their source and passing integration/browser tests. Preserve incomplete unrelated changes. Controller/service/repository/database behavior remains unchanged.
RAG remains a separate planned change with explicit acceptance criteria and unresolved provider/budget decisions. Report citations distinguish repository evidence from the proposed pgvector option.
## Risks / Trade-offs
Old active change descriptions can contain obsolete contracts; normalize creation/publication and tree-loading descriptions against tested behavior. Local benchmark results do not establish a production SLA. Run global OpenSpec validation plus all user-requested tests before merging.
