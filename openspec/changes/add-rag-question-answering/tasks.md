## 1. Decisions and baseline
- [ ] 1.1 Select embedding/generation models, data policy, budget and operating limits.
- [ ] 1.2 Prepare at least 30 labelled Russian questions and agree measured acceptance thresholds.
## 2. Indexing
- [ ] 2.1 Add provider ports, opt-in vector schema/migration and versioned chunk repository.
- [ ] 2.2 Implement durable idempotent indexing after publication/update, backfill and deletion invalidation.
## 3. Retrieval and answering
- [ ] 3.1 Implement ACL-filtered retrieval and permission/version rechecks before and after generation.
- [ ] 3.2 Add grounded prompts, verified citations, insufficient-evidence handling and bounded provider calls.
- [ ] 3.3 Add question API and UI with loading, sources and controlled error states.
## 4. Verification and rollout
- [ ] 4.1 Test ACL leakage, prompt-injection examples, stale versions, retries, disabled mode and provider failures.
- [ ] 4.2 Run labelled evaluation and record retrieval quality, citation validity, latency and cost.
- [ ] 4.3 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы.
- [ ] 4.4 Проверка критериев качества: [quality gates](../../quality-gates.md), all Java/Node/Playwright tests, PostgreSQL/pgvector integration and provider contract tests.
- [ ] 4.5 Enable feature after approved deployment settings and successful backfill; document rollback and update registry.

Status: planned. Requirements/design prepared on 2026-10-25; implementation tasks intentionally remain unchecked.
