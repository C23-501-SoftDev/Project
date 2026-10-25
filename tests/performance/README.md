# Document-view benchmark (US4.4.1 / issue #23)

Use an isolated local PostgreSQL database whose name ends in `_test`; the fixture adds 50 spaces and 5000 metadata-only documents plus one real Markdown document. It does not clean up existing data. The metadata-only documents are used only in the sidebar; the measured document is created through the API and stored by JGit.

Install the E2E dependencies and Chromium, start the application against that database, then run from the repository root:

```sh
PERF_ALLOW_TEST_DB=1 E2E_BASE_URL=http://127.0.0.1:18080 \
PERF_DATABASE=knowledge_base_test PERF_DB_PORT=55433 PERF_DB_USER=kb_user \
node tests/performance/document-view.cjs /tmp/document-view-results.json
```

`psql` must be on PATH (or set `PSQL`). `PGPASSWORD`, `PERF_LOGIN`, `PERF_PASSWORD` and `PLAYWRIGHT_BROWSERS_PATH` can be provided for the local environment. Never run this against shared application data.

Five navigations in one Chromium context measure time until the rendered document heading is visible, initial SSR response size, and sidebar document node count. Loopback networking, one user, warm JVM and browser caching differ from production. These results verify this fixture, not a universal two-second guarantee or a concurrent-load SLO.

## Measured results — 2026-10-25

Environment: macOS arm64, Java 21.0.7, PostgreSQL 18.6, Chromium via Playwright 1.59.1, 1440×900 viewport, loopback, one user. Both runs add 50 spaces × 100 documents after the full E2E suite, plus one real document (~1500 words). Residual E2E fixtures differ slightly because the second suite includes new tree tests.

| Measurement | Before | After |
| --- | ---: | ---: |
| Document ready, five runs | 708–1097 ms | 63–106 ms |
| Median | 741 ms | 65 ms |
| Initial HTML | 2,093,441 bytes | 84,428 bytes |
| Sidebar document nodes at content readiness | 5062 | 1 |

The median improved about 11.4× and initial HTML shrank about 96%. Both local runs were already below the two-second target; the change removes unnecessary scaling with documents in closed spaces. Initial SSR now contains zero document tree rows; the one visible node arrives through the current-space fragment request. Raw observations are in the adjacent before/after JSON files. Space controls are still rendered eagerly, and one very large space still loads its complete tree. Concurrent-load and network-latency testing remain necessary for a production SLA.
