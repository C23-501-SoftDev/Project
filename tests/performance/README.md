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

## Concurrent acceptance harness — 2026-10-26

`load-acceptance.cjs` adds a repeatable acceptance run for [US4.4.1](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog-descriptions/E4%20%D0%90%D0%B4%D0%BC%D0%B8%D0%BD%D0%B8%D1%81%D1%82%D1%80%D0%B8%D1%80%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B5%20%D0%B8%20%D0%91%D0%B5%D0%B7%D0%BE%D0%BF%D0%B0%D1%81%D0%BD%D0%BE%D1%81%D1%82%D1%8C%20%28Admin%20%26%20Security%29/F4.4%20%D0%9F%D1%80%D0%BE%D0%B8%D0%B7%D0%B2%D0%BE%D0%B4%D0%B8%D1%82%D0%B5%D0%BB%D1%8C%D0%BD%D0%BE%D1%81%D1%82%D1%8C%20%D1%81%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D1%8B/US4.4.1%20%D0%9E%D0%BF%D1%82%D0%B8%D0%BC%D0%B8%D0%B7%D0%B0%D1%86%D0%B8%D1%8F%20%D0%B2%D1%80%D0%B5%D0%BC%D0%B5%D0%BD%D0%B8%20%D0%BE%D1%82%D0%BA%D0%BB%D0%B8%D0%BA%D0%B0%20%D0%BF%D1%80%D0%B8%20%D0%BF%D1%80%D0%BE%D1%81%D0%BC%D0%BE%D1%82%D1%80%D0%B5/description.md). The original criterion is that a document is displayed within two seconds under normal load; it does not define a user count. The user selected **25 active readers** as the normal profile on 2026-10-26. A separate 50-reader profile observes overload behaviour; it does not redefine normal load.

The profiles and gates below are defined before the first acceptance measurement. Failed samples and outliers remain in the report. A failed run is not made green by changing its thresholds.

| Profile | Accounts | Corpus used for requests | Acceptance |
| --- | ---: | --- | --- |
| `normal-distributed` | 25 | Ten real articles across ten of the 50 regular spaces | Required |
| `normal-large-space` | 25 | A real article in one space with 5000 metadata documents | Required |
| `stress-large-space` | 50 | The same large space | Observational; same metrics/gate diagnostics are reported |

Each profile starts new operations for at least 60 seconds. Every account is a distinct non-admin `READER`. Of the configured accounts, one drives Chromium and the others run HTTP workloads. One HTTP cycle loads the SSR page, starts the current-space tree and header create-permission requests, reads the document JSON, reads document permissions and lists attachments; a fixed one-second think time follows the completed cycle. The sixth request, `/api/spaces?requiredAccess=WRITE&size=1`, must return an empty array for these readers without write grants. The browser measurement account navigates continuously, with `browserThinkTimeMs=0`; its context setup and completed navigation requests still consume time. This is a **closed-loop** interactive workload: slower responses reduce the achieved arrival rate. The report records achieved cycles/requests per second and does not claim an open-loop capacity limit.

The initial `load-acceptance-run-1` is retained as a baseline with five background endpoints; it omitted the header create-permission request in HTTP workers. Workload version 2 adds that real UI request and observes late browser requests before completing a navigation. The original latency/error/sample gates are unchanged; the additional endpoint gets the same p95 ≤2000 ms gate. Final acceptance uses version 2, not a reinterpretation of the earlier raw observations.

The fixture adds 50 spaces × 100 metadata-only articles, one space × 5000 metadata-only articles, and 11 real published Markdown documents created through the normal API/JGit path. The large space therefore has 5001 rows including its real measured article. The fake file paths belong only to sidebar metadata: the runner never navigates to them. All viewed documents have the same multi-section Markdown with lists and about 2000 words; exact character/word counts and SHA-256 are recorded. This tests many spaces and one wide tree, not a deeply nested hierarchy or a varied distribution of document sizes.

### Gates fixed before measurement

For both normal profiles:

- every measured browser content-ready navigation must be at most 2000 ms, including fresh-cache navigations; p95 and maximum are both checked;
- p95 for each HTTP endpoint group (SSR page, document JSON, permissions, attachments, tree, header create-permissions) must be at most 2000 ms;
- p95 for the complete HTTP content chain (SSR → document JSON → permissions) must also be at most 2000 ms;
- there must be zero HTTP validation/transport errors, browser request/script/navigation errors and failed workers;
- the load window must be at least 60 seconds, with at least 100 HTTP samples and 20 browser navigations: at least ten fresh-cache and ten repeated navigations.

A profile with too few browser samples fails; the runner does not silently continue browser sampling after background load has stopped. In-flight operations are drained and their full durations/errors remain recorded. Overall acceptance requires both normal profiles; running only a subset cannot report overall acceptance as passed. The stress profile is always reported separately.

### Browser measurement and network model

Each pair uses a fresh browser context with an authenticated reader cookie, an empty HTTP cache and empty local storage. The second navigation repeats the same document in that context. **Cold means browser cache only**: the JVM, PostgreSQL and OS file cache are not restarted between requests. The browser viewport is 1440×900.

Chrome DevTools Protocol applies a configured 100 ms latency, 20 Mbps download and 5 Mbps upload only to the Chromium context. This is an emulated network condition, not a measured physical 100 ms round trip. Background HTTP clients connect directly over loopback without that delay. Do not add their timings to browser timings or describe the whole run as network-throttled.

Content-ready is measured from the start of `page.goto` until the expected article heading is visible in `#documentContent`, followed by two animation frames to allow painting. This includes the application's actual document and permissions fetches. The runner separately waits for the current tree's `data-loaded=true`, counts its DOM rows and records tree-ready time. Tree-ready is diagnostic; the original page-display acceptance gate is applied to **article content-ready**, and tree endpoint p95 has its own HTTP gate. Initial SSR response timings/bytes, all observed browser response statuses, request failures and page errors are also retained.

Listeners stay attached until the header response is validated and all tracked navigation requests have finished, followed by 100 ms without network activity. Draining is bounded by the configured ten-second request timeout on both successful and failed navigation paths. A failed/unfinished header request, failed resource or undrained request makes the browser sample fail; it cannot disappear when listeners are removed. This drain does not add to the already captured content-ready metric, but it does affect total navigation duration and sampling throughput.

### Isolated setup and commands

Use a separate application process, database and Git/blob directories. The default database is `knowledge_base_performance_test`, PostgreSQL port 55433, application port 18082. Keep `AI_ENABLED=false`, `RAG_ENABLED=false` and notifications disabled. Do not run Maven, E2E, model inference or another benchmark concurrently on the same host. Those processes change CPU/memory availability and would invalidate comparisons.

The runner only accepts a loopback HTTP origin, a simple database name ending in `_test`, and explicit `PERF_ALLOW_TEST_DB=1`. Before any fixture API mutation, it inserts a uniquely named space in the specified test database and verifies the same space through the application API. This detects an app accidentally configured against another database. It never drops a schema, resets data or removes existing records. The fixtures and isolation probe remain for inspection.

Install the existing `tests/E2E` npm dependencies and Chromium, then run from the repository root:

```sh
node tests/performance/load-acceptance.cjs --validate-only
node --test tests/performance/load-acceptance.test.cjs

export PERF_ALLOW_TEST_DB=1
export PERF_BASE_URL=http://127.0.0.1:18082
export PERF_DATABASE=knowledge_base_performance_test
export PERF_DB_PORT=55433 PERF_DB_USER=kb_user
export PSQL=/opt/homebrew/opt/postgresql@18/bin/psql  # or psql on PATH
# Set PGPASSWORD only if the isolated server requires it.
# Set PLAYWRIGHT_BROWSERS_PATH if Chromium was installed in a custom directory.

node tests/performance/load-acceptance.cjs \
  --state /private/tmp/load-acceptance-fixtures.json --seed-only

node tests/performance/load-acceptance.cjs --resume \
  --state /private/tmp/load-acceptance-fixtures.json \
  --report /private/tmp/load-acceptance-run-1.json
```

The test administrator defaults to `admin/admin123` (`PERF_LOGIN` / `PERF_PASSWORD` can override it). Fixture readers use the public test-only password `PerfOnly123!`; `PERF_READER_PASSWORD` can replace it. Keep that value unchanged on resume. Passwords, cookies and CSRF tokens are not written to the report/state. Do not use these credentials on a deployed instance.

`PERF_NORMAL_USERS`, `PERF_STRESS_USERS` and `PERF_DURATION_SECONDS` are configurable for additional explicitly labelled experiments. Defaults are 25, 50 and 60; duration below 60 seconds is rejected. The final accepted user profile remains 25/50, regardless of other experiments. Network/think-time/gates are versioned in the runner and recorded in each report.

`--profiles normal-large-space` can isolate a diagnosis, but overall acceptance remains incomplete. A new `--report` path is mandatory for each run: existing final or partial reports are never overwritten. The fixture state checkpoints IDs after creation and verifies document content on resume; interruptions do not require reseeding all records. A report `.partial.json` checkpoints observations every five seconds; after interruption, start a new report with `--resume` to rerun full profiles on the same fixtures. Never splice partial runs together into a passing acceptance report.

### Interpreting results

Exit code 0 means both normal profiles completed and passed their unchanged gates. The JSON contains raw HTTP/browser samples, summaries, status counts, achieved throughput, requested/actual durations, fixture identifiers, CPU/RAM host information, browser/Node/PostgreSQL versions, source commit and runner SHA-256. `workingTreeChanged` indicates whether the measurement included uncommitted changes.

Even a passing result applies only to the recorded machine, data shape, closed-loop load and network emulation. It does not prove a production SLA, large-scale ACL performance, simultaneous editing, export throughput or performance during RAG inference. The 2026-10-25 single-user before/after numbers above remain historical measurements, not evidence of concurrent acceptance.

## Final acceptance results — 2026-10-26

Run3 passed both required 25-reader profiles with the original latency/error/sample gates and the complete six-endpoint workload. All normal browser content-ready observations were below two seconds; the worst was **591.94 ms**. The separate 50-reader stress profile also had zero errors. The full 5001-row tree remained present in the browser.

| Profile | HTTP samples (all 200) | Browser cold / repeat | Content p95 / max | Tree HTTP p95 | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `normal-distributed` | 8208 | 41 / 41 | 583.33 / 591.94 ms | 23.85 ms | PASS |
| `normal-large-space` | 8352 | 26 / 26 | 572.00 / 580.46 ms | 37.47 ms | PASS |
| `stress-large-space` | 16980 | 26 / 26 | 567.33 / 690.89 ms | 42.88 ms | Observational pass |

The earlier five-endpoint baseline failed on the large tree (HTTP tree p95 7055.93 ms). A six-endpoint diagnostic with template caching/SpEL compilation still failed (7148.96 ms). The final run used `DocumentTreeRenderer` and restored the original application settings, including `spring.thymeleaf.cache=false`; changing gates or reducing the tree was not part of the fix.

[Full results in Russian, all endpoint distributions, environment, run history and lossless raw reports](results/README.md). The report identifies clean source commit `f9a7b12`, the runner hash and the measured JAR hash. These are local acceptance results for the declared corpus and network model, not a production SLA. The 2026-10-25 historical single-user measurements above remain unchanged.
