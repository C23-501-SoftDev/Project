## 1. Evidence and implementation
- [x] 1.1 Confirm the user's cross-article scenario and inspect prompt, retrieval and current acceptance coverage.
- [ ] 1.2 Add configurable per-document retrieval limits and broaden default context with PostgreSQL regression tests.
- [ ] 1.3 Implement grounded multi-article synthesis instructions while preserving provider validation and source rechecks.
- [ ] 1.4 Extend real-model evaluation with required source sets, complete evidence checks and baseline measurements.

## 2. Verification and documentation
- [ ] 2.1 Run the full old and expanded local Ollama evaluations, preserve raw reports and manually review synthesis answers.
- [ ] 2.2 Проверка критериев качества — complete all [repository quality gates](../../quality-gates.md), including fresh-database Playwright and real pgvector tests.
- [ ] 2.3 Update RAG operational documentation, feature registry, BACKTRACKER semantics and `tests/E2E/E2E_RESULTS.md` with actual evidence.
- [ ] 2.4 Sync and archive the specification, merge the task branch into main without MR, push and update the local demo while preserving its data.
