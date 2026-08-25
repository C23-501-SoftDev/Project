# Document Version Diff Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать пользователю с правом чтения построчное GitHub-style сравнение двух сохранённых версий одного документа.

**Architecture:** `DocumentVersionService` проверяет, что обе SHA зарегистрированы у документа, и делегирует чтение Git-объектов порту контента. JGit adapter формирует нейтральную доменную модель строк, которую REST endpoint возвращает JSON; history page безопасно отображает её через DOM `textContent`.

**Tech Stack:** Java 17+, Spring Boot 3.4, Spring MVC/Security, Spring Data JPA, JGit, Thymeleaf, vanilla JavaScript, JUnit 5/MockMvc.

## Global Constraints

- Только JGit: не выполнять Git shell-команды из приложения.
- Сравнивать строго `Document.gitFilePath`; SHA должен существовать в `versions` для указанного документа до чтения Git.
- API: `GET /api/documents/{id}/diff?from=<40 hex>&to=<40 hex>`; одинаковые/некорректные SHA → 400, чужая/отсутствующая версия → 404, нет READ → 403, превышен лимит → 422.
- Текст документа никогда не вставляется через `innerHTML`.

---

### Task 1: Domain contracts and version lookup

**Files:**
- Create: `backend/src/main/java/com/knowledgebase/domain/model/{DocumentDiff,DiffLine,DiffLineType}.java`
- Modify: `backend/src/main/java/com/knowledgebase/domain/repository/{DocumentContentRepository,DocumentVersionRepository}.java`
- Modify: `backend/src/main/java/com/knowledgebase/infrastructure/persistence/repository/{DocumentVersionJpaRepository,DocumentVersionRepositoryImpl}.java`
- Test: `backend/src/test/java/com/knowledgebase/application/service/DocumentVersionServiceTest.java`

- [ ] Write a failing service test proving an unknown SHA is rejected before Git is called.
- [ ] Run the targeted test and verify it fails because the diff use case does not exist.
- [ ] Add immutable domain records, version lookup by `(documentId, gitHash)`, and the Git diff port signature.
- [ ] Run the targeted test after Task 2 supplies the service.

### Task 2: JGit diff and application use case

**Files:**
- Create: `backend/src/main/java/com/knowledgebase/application/service/DocumentVersionService.java`
- Modify: `backend/src/main/java/com/knowledgebase/infrastructure/repository/git/JGitDocumentContentRepository.java`
- Test: `backend/src/test/java/com/knowledgebase/integration/JGitDocumentContentRepositoryIntegrationTest.java`
- Test: `backend/src/test/java/com/knowledgebase/application/service/DocumentVersionServiceTest.java`

- [ ] Write a failing integration test with two commits that change the document and another file; expect only removed/context/added lines of the document with before/after numbers.
- [ ] Run it and verify the failure is caused by the missing diff operation.
- [ ] Implement one-path tree reading with JGit `DiffFormatter`, parse unified lines to `DiffLine`, handle a missing file as empty, and enforce a configurable line limit.
- [ ] Implement SHA format/equality validation and registered-version validation in the service.
- [ ] Run service and JGit tests; verify they pass.

### Task 3: REST endpoint, errors, and API integration

**Files:**
- Create: `backend/src/main/java/com/knowledgebase/interfaces/rest/controller/DocumentVersionController.java`
- Create: `backend/src/main/java/com/knowledgebase/interfaces/rest/dto/response/{DocumentDiffResponse,DiffLineResponse}.java`
- Modify: `backend/src/main/java/com/knowledgebase/interfaces/rest/advice/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/knowledgebase/domain/exception/*` only if a dedicated 422 error is necessary.
- Test: `backend/src/test/java/com/knowledgebase/integration/DocumentVersionDiffIntegrationTest.java`

- [ ] Write a failing MockMvc test for authorised 200 and a second test for a hash registered to another document returning 404.
- [ ] Run the tests and verify they fail because the route is absent.
- [ ] Add controller, mapper/DTOs, read authorization and validated exception mapping without exposing Git content in errors.
- [ ] Run targeted MockMvc tests and verify 200/400/403/404/422 behaviour.

### Task 4: History-page diff UI and documentation

**Files:**
- Modify: `backend/src/main/resources/templates/pages/document-history.html`
- Create: `backend/src/main/resources/static/js/document-history.js`
- Modify: `backend/src/main/resources/static/css/main.css`
- Modify: `backend/src/main/resources/templates/layout.html`
- Modify: `BACKTRACKER.md`, `openspec/changes/document-version-diff-mode/tasks.md`
- Test: a focused browserless JavaScript test only if an existing JS test runner is present; otherwise validate safe DOM construction by inspection and backend contract tests.

- [ ] Add history controls accepting two explicitly selected SHA, loading/empty/error states, and a `document-history.js` renderer using `textContent`.
- [ ] Add CSS for context/removed/added rows and line numbers; load the module only on the history page.
- [ ] Update BACKTRACKER endpoint status and task checkboxes for completed work.
- [ ] Run `mvn clean compile`, `mvn test`, `git diff --check`; leave smoke-check pending unless PostgreSQL is available.
