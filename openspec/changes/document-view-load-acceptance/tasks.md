## 1. Profile and tooling
- [x] 1.1 Confirm normal concurrency and define immutable acceptance gates.
- [x] 1.2 Implement isolated resumable fixtures, real sessions and concurrent HTTP/browser workloads.
- [x] 1.3 Test the runner gates, failures and sample accounting.
## 2. Acceptance and evidence
- [ ] 2.1 Run both 25-user normal profiles and the separate 50-user stress profile; preserve raw observations.
- [ ] 2.2 Resolve demonstrated bottlenecks without weakening gates and repeat affected profiles if needed.
- [ ] 2.3 Document exact environment, results, reproducible commands and capacity limits.
## 3. Verification and completion
- [x] 3.1 Проверка критериев качества: [quality gates](../../quality-gates.md), all Java, Node and Playwright tests; PostgreSQL/pgvector tests included.
- [ ] 3.2 Update registry and project overview; synchronize and archive the completed specification.

Verification before acceptance rerun: Maven clean verify 251/251 (including 14 real PostgreSQL/pgvector cases), Node 12/12, Playwright 154/154 on a fresh isolated schema, strict OpenSpec validation passed.
