# Safe Document Version Rollback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a published document's text from a registered historical Git version by creating a new current snapshot and version entry.

**Architecture:** The application service validates the target version before accessing Git, reads its blob without moving Git `HEAD`, and commits that text at the document's current path with current metadata. The DB transaction then persists the indexed document and new version; Git failures leave DB untouched, while DB failures retain an observable orphan SHA without resetting Git.

**Tech Stack:** Java 21, Spring Boot, Spring Security, JGit, JPA, MockMvc, Node.js tests.

**Spec:** `openspec/changes/safe-document-version-rollback/specs/document-version-rollback/spec.md`; `openspec/changes/safe-document-version-rollback/design.md`

## Global Constraints

- Restore only text of a published document; do not restore document metadata, attachments, structure, branches, or drafts.
- Only a user with EDIT permission may invoke the API; malformed or foreign SHA must be rejected before Git access.
- Never use Git `checkout`, `reset`, history rewriting, or force push.
- Persist the restored state as a new Git commit and a new `versions` row with the editor as author.
- Use Git first and a single database transaction afterwards; log the Git SHA if DB persistence fails.
- The history page asks for explicit confirmation and refreshes only its version data after success.

---

### Task 1: Read a document blob from an immutable Git version

**Files:**
- Modify: `backend/src/main/java/com/knowledgebase/domain/repository/DocumentContentRepository.java`
- Modify: `backend/src/main/java/com/knowledgebase/infrastructure/repository/git/JGitDocumentContentRepository.java`
- Test: `backend/src/test/java/com/knowledgebase/integration/JGitDocumentContentRepositoryIntegrationTest.java`

**Interfaces:**
- Produces: `Optional<String> readDocumentVersion(String gitFilePath, String gitHash)`.

- [ ] **Step 1: Write a failing integration test** that saves two snapshots, reads the first SHA by path, and asserts the returned text is the original text while repository `HEAD` remains the second SHA.
- [ ] **Step 2: Run the test** and confirm it fails because `readDocumentVersion` does not exist.
- [ ] **Step 3: Add the port method and JGit implementation** using `TreeWalk.forPath` on the requested commit tree and the existing path normalization; return empty when the file is absent and do not alter `HEAD`.
- [ ] **Step 4: Run the integration test** and confirm it passes.

### Task 2: Restore a registered version transactionally

**Files:**
- Modify: `backend/src/main/java/com/knowledgebase/application/service/DocumentVersionService.java`
- Test: `backend/src/test/java/com/knowledgebase/application/service/DocumentVersionServiceTest.java`

**Interfaces:**
- Consumes: `readDocumentVersion(String, String)` and `saveDocumentSnapshot(...)`.
- Produces: `DocumentVersion restoreVersion(Long documentId, String gitHash, Long editorId)`.

- [ ] **Step 1: Write failing unit tests** for malformed and unregistered SHA, asserting no Git interaction, and for a valid version asserting a new version is returned with a fresh SHA, current path, and editor ID.
- [ ] **Step 2: Run the unit tests** and confirm the valid use case fails because `restoreVersion` does not exist.
- [ ] **Step 3: Implement the minimal Git-to-DB workflow**: normalize and validate SHA, load document and registered version, read selected text, load editor, write a new snapshot with current metadata, save the document and `DocumentVersion`, then audit and publish the update event.
- [ ] **Step 4: Add and run failing error-path tests** for Git read/write failure and version persistence failure; implement propagation with no DB save before Git success and error logging of the created SHA after a persistence failure.
- [ ] **Step 5: Run the service test class** and confirm it passes.

### Task 3: Expose the protected restore API

**Files:**
- Modify: `backend/src/main/java/com/knowledgebase/interfaces/rest/controller/DocumentVersionController.java`
- Test: `backend/src/test/java/com/knowledgebase/integration/DocumentVersionRollbackIntegrationTest.java`

**Interfaces:**
- Consumes: `DocumentVersionService.restoreVersion(Long, String, Long)`.
- Produces: `POST /api/documents/{id}/versions/{gitHash}/restore` with `201 Created` and `DocumentVersionResponse`.

- [ ] **Step 1: Write a failing MockMvc test** for an editor restoring the first of two versions; assert `201`, a new SHA different from the selected SHA, current content equal to the old text, and three rows in history.
- [ ] **Step 2: Run the integration test** and confirm it fails with no matching endpoint.
- [ ] **Step 3: Add the `POST` controller method** with `canWrite` pre-authorization, OpenAPI responses, `Authentication` principal extraction, and `201 Created` response mapping.
- [ ] **Step 4: Add tests for reader `403`, foreign SHA `404`, and malformed SHA `400`; run the class** and confirm it passes.

### Task 4: Make restoration deliberate in the history UI

**Files:**
- Modify: `backend/src/main/resources/templates/pages/document-history.html`
- Modify: `backend/src/main/resources/static/js/document-history.js`
- Test: `backend/src/test/javascript/document-history.test.js`

**Interfaces:**
- Consumes: selected version SHA and `POST /api/documents/{id}/versions/{gitHash}/restore`.
- Produces: editor-only restore action, confirmation dialog, CSRF-safe POST, success message, and refreshed version list.

- [ ] **Step 1: Write a failing JavaScript test** that cancels confirmation and asserts no POST occurs; add a second test accepting confirmation and asserting the POST URL/header plus `loadVersions()` refresh.
- [ ] **Step 2: Run the Node test file** and confirm the new tests fail because the restore action is absent.
- [ ] **Step 3: Add the editor-only button and event handler**: ask `window.confirm`, send an empty `POST` with the CSRF header, display success/error status, and call `loadVersions()` after `201`.
- [ ] **Step 4: Run the Node test file** and confirm it passes.

### Task 5: Record and verify the feature

**Files:**
- Modify: `BACKTRACKER.md`
- Modify: `openspec/feature-registry.json`
- Modify: `openspec/changes/safe-document-version-rollback/tasks.md`

- [ ] **Step 1: Update documentation** to show the restore endpoint and US2.2.4 implementation status.
- [ ] **Step 2: Run targeted Java, integration, and UI tests**, then `mvn clean compile` and the full Maven test suite using JDK 21.
- [ ] **Step 3: Check the worktree** with `git diff --check` and `git status --short`; update the OpenSpec task checkboxes only for verified work.
