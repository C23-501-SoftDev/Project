## 1. Evidence and implementation
- [x] 1.1 Confirm the user's cross-article scenario and inspect prompt, retrieval and current acceptance coverage.
- [x] 1.2 Add configurable per-document retrieval limits and broaden default context with PostgreSQL regression tests.
- [x] 1.3 Implement grounded multi-article synthesis instructions while preserving provider validation and source rechecks.
- [x] 1.4 Extend real-model evaluation with required source sets, complete evidence checks and baseline measurements.

## 2. Verification and documentation
- [x] 2.1 Run the full old and expanded local Ollama evaluations, preserve raw reports and manually review synthesis answers. The 40-question run includes all unchanged original 30 questions: 24/24 original answers, 6/6 refusals and 10/10 complete cross-article answers (baseline 7/10). Both supplementary direct-provider probes passed; raw responses manually reviewed.
- [x] 2.2 Проверка критериев качества — complete all [repository quality gates](../../../quality-gates.md), including fresh-database Playwright and real pgvector tests. 281 Java, 12 Node, 7 Python, 158 Playwright passed without failures or skips; strict OpenSpec passed.
- [x] 2.3 Update RAG operational documentation, feature registry, BACKTRACKER semantics and `tests/E2E/E2E_RESULTS.md` with actual evidence.
- [x] 2.4 Prepare completed artifacts and the specification delta for synchronization and archival.

Delivery after acceptance: merge the branch into main without MR, push and update the local demo while preserving its data, as authorized by the user.

Local demo smoke: the updated verified JAR on port 18081 returned HTTP 200 with both backup and log articles for the retention comparison; the existing corpus remains 15 documents.
