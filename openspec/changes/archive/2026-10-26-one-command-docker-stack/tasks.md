## 1. Compose and container runtime

- [x] 1.1 Prepare the local Docker/Compose/build toolchain and record the actual ARM64 Colima resources and versions.
- [x] 1.2 Implement the multi-stage Java 21 build, non-root runtime and minimal image/build context without host source, SSH or Git-config mounts.
- [x] 1.3 Implement idempotent secret/data initialization and four persistent named volumes for PostgreSQL, content, models and secrets.
- [x] 1.4 Configure PostgreSQL 18/pgvector, local Ollama, model preparation and health-based dependencies so default Compose works without `.env` and exposes only the app on host loopback.

## 2. Helpers and operator documentation

- [x] 2.1 Align Make/dev/bootstrap/E2E helpers and the optional environment example with project-scoped Compose services; isolate test projects and avoid global volume cleanup.
- [x] 2.2 Write `docs/DOCKER_LOCAL_RU.md` with one-command startup, explicit wait, logs/status, resources, persistence/reset, migration and native fallback.
- [x] 2.3 Replace obsolete README Docker onboarding and duplicate API descriptions with current startup instructions and maintained documentation/API links.

## 3. Real Docker verification and migration

- [x] 3.1 Verify fresh project startup without `.env`, healthy dependency/model completion, app UID, private database/model ports and absence of host credential/source mounts.
- [x] 3.2 Capture a consistent native PostgreSQL/Git/blob snapshot of all current user data with writers stopped, retain rollback data and restore only into the intended empty user Docker project; verify document/version/blob references and model digests.
- [x] 3.3 Verify repeated startup and ordinary `down`/`up` preserve user corpus, secret identities and models; confirm article/history, export and a real local RAG answer with two valid sources.
- [x] 3.4 Record actual CPU inference/readiness outcomes and limitations separately from historical native model-quality results; leave the user's verified runtime available.

## 4. Regression and completion

- [x] 4.1 Проверка критериев качества: run the full repository checks in [openspec/quality-gates.md](../../../quality-gates.md), including Maven, Node, Python, isolated Playwright and strict OpenSpec validation; record actual results.
- [x] 4.2 Verify container initialization/readiness and helper regressions, including preservation on retries and test-project isolation, without deleting user volumes.
- [x] 4.3 Review pending files for secrets or runtime data; update the feature registry and synchronize/archive `docker-runtime` only after the evidence is complete.

## Verification evidence — 2026-10-26

Homebrew Colima 0.10.3 runs an ARM64 Virtualization.framework VM with 6 CPUs, 12 GiB RAM and a 40 GiB data disk. Docker CLI 29.9.2, Engine 29.5.2, Compose 5.6.1 and Buildx 0.38.1 are confirmed. The user-level Colima Homebrew service is enabled for login startup; obsolete Desktop plugin/credential configuration was backed up before repair. This establishes engine readiness, not application acceptance.

The Docker branch passed Maven clean verify: 281 tests including 17 real PostgreSQL/pgvector tests, no failures/skips; Node 12, Python RAG 7 and native-runtime Python 12 passed. PostgreSQL tests used a separate freshly created database, removed after the run. Local Maven log: `.local/logs/docker-maven-verify.log`.

Container initialization has 17 passing isolated filesystem/mock-HTTP tests; the project-scoped helper suite has 15 passing tests (13 fake-CLI behavioral and 2 PowerShell/Dev Container static checks). Compose configuration validates with `--env-file /dev/null`; the actual resolved test backend dependency graph contains only app, runtime-init, postgres and models-init, so test setup neither pulls nor starts Ollama. Default Compose RAG remains enabled.

Real Docker builds completed for app/runtime-init/models-init, all linux/arm64; app image config specifies UID/GID 1000:1000. Log: `.local/logs/docker-app-image-build.log`. Image packaging skips Maven tests and is not counted as another Java test run. Target `trpo` PostgreSQL 18.6 became healthy on fresh volumes before corpus migration.

Host Chromium against a fresh Docker production backend/PostgreSQL passed 158/158 Playwright tests in 1.1 minutes, workers=1, retries=0. The isolated Compose project was `kb-docker-browser-20261026`; cleanup removed only its resources. Logs: `.local/logs/docker-host-e2e.log` and `.local/logs/docker-host-e2e-setup.log`; reports: `.local/e2e-results/docker-host-20261026/`. The test override excluded Ollama; mocked RAG UI results are not real-model acceptance.

The full `./run-e2e.sh` Linux Chromium/container run passed 158/158 tests in 1.3 minutes, workers=1, retries=0, no failures/skips; both browser and runner exited 0. Independent cleanup checks found no containers, volumes or networks belonging to its isolated project `kb-e2e-jrrmvf1b`. Evidence: `.local/e2e-results/kb-e2e-jrrmvf1b/result.json`; log: `.local/logs/docker-linux-e2e.log`. An earlier attempt exited 126 before tests because the runner lacked its executable bit; mode 100755 and direct-entrypoint helper regression checks fixed this, with all 15 helper tests passing again. The original failure log is retained at `.local/logs/docker-linux-e2e-attempt1-permission.log`.

Cold acceptance passed on initially empty isolated volumes: both models were downloaded automatically by Ollama, without manual model seeding. One network retry was needed during Qwen download; only the cold model/init services were restarted, retaining partial downloads. Final wait exited 0, all three long-lived services were healthy and init jobs exited 0. `.local/docker-acceptance-cold.json` records ARM64, UID 1000, loopback-only app port 18082, named volumes without host mounts, PostgreSQL 18.6/vector 0.8.7 and model digests; Ollama reported `size_vram=0` (CPU). Seed-admin login and RAG enabled status passed in `.local/docker-cold-api.json`; download/readiness log: `.local/logs/docker-cold-models-complete.log`. Retry evidence: `.local/logs/docker-cold-model-retry.json`. This was successful cold preparation with a network retry, not an uninterrupted first attempt.

The complete current native snapshot `.local/backups/docker-2026-10-26-213642` contained 17 documents, 15 versions, 1 attachment, 2 spaces and 1 user, including two documents and an attachment added after the old 15-document demo. Restore into empty `trpo` passed all-table fingerprints, content/model file hashes, 15 Git SHA:path references and the attachment reference: `restore-20261026-213730.json` in that backup. No earlier demo dump replaced these newer additions.

The user then published document 17, creating version 16. Its expected mismatch against the original migration fingerprint was not treated as a migration defect and no user edit was reverted. A new quiescent baseline `.local/docker-lifecycle-baseline.json` captured 17 documents/16 versions/1 attachment/2 spaces/1 user. Ordinary down/up preserved all current tables, references, content, model and secret identities exactly; evidence: `.local/docker-lifecycle-preservation.json` and `.local/logs/docker-lifecycle-preservation.log`. Real CPU RAG returned 14-day backup/7-day log retention with valid document 9/14 citations/version SHAs before and after restart: 21.908s and 17.433s respectively. All four export formats passed. The post-restart check ran with native PostgreSQL/Ollama stopped; Docker app remains available on 8080.

Final implementation review found no remaining defects. Pending source files contain no user corpus, model weights or generated credentials; runtime/demo evidence remains ignored under `.local/`. The matching feature registry entry is recorded as done, and this completed change is synchronized into `docker-runtime` and archived. Strict OpenSpec validation passes for all 38 active changes/specifications.
