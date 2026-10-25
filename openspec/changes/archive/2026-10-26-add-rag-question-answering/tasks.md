## 1. Decisions and baseline
- [x] 1.1 Select embedding/generation models, data policy, budget and operating limits.
- [x] 1.2 Prepare at least 30 labelled Russian questions and agree measured acceptance thresholds.
## 2. Indexing
- [x] 2.1 Add provider ports, opt-in vector schema/migration and versioned chunk repository.
- [x] 2.2 Implement durable idempotent indexing after publication/update, backfill and deletion invalidation.
## 3. Retrieval and answering
- [x] 3.1 Implement ACL-filtered retrieval and permission/version rechecks before and after generation.
- [x] 3.2 Add grounded prompts, verified citations, insufficient-evidence handling and bounded provider calls.
- [x] 3.3 Add question API and UI with loading, sources and controlled error states.
## 4. Verification and rollout
- [x] 4.1 Test ACL leakage, prompt-injection examples, stale versions, retries, disabled mode and provider failures.
- [x] 4.2 Run labelled evaluation and record retrieval quality, citation validity, latency and cost.
- [x] 4.3 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы.
- [x] 4.4 Проверка критериев качества: [quality gates](../../../quality-gates.md), all Java/Node/Playwright tests, PostgreSQL/pgvector integration and provider contract tests.
- [x] 4.5 Enable feature after approved deployment settings and successful backfill; document rollback and update registry.

Status: complete, 2026-10-26. Local Ollama deployment and 15-document backfill verified; production serving remains opt-in.

Verification: Maven clean verify 246/246, Node 3/3, Playwright 154/154 with no failures/skips/retries. Real Ollama evaluation v2: 24/24 substantive answers and expected citations, 6/6 unsupported refusals, zero invalid citations/canary. p50 1720ms / p95 2023ms on the synthetic local corpus. See [full results](../../../../tests/rag/results/README.md).
