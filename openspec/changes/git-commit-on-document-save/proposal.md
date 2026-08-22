## Why

Сохранение документа уже записывает Markdown через JGit, но не создаёт полноценную версию: коммит может иметь системного автора, изменения только атрибутов не обязательно фиксируются, а метаданные Git-коммита не попадают в таблицу `versions`. Поэтому история не соответствует сохранениям редактора.

Каждое сохранение изменённого опубликованного документа должно создавать отдельную версию: сервер сохраняет итоговый файл в локальном Git-репозитории, создаёт коммит от имени редактора и фиксирует SHA, автора и время в PostgreSQL.

## What Changes

- Добавить domain-модель и persistence-порт для записи метаданных версии в существующую таблицу `versions`.
- Изменить Git-порт так, чтобы сохранение файла возвращало результат JGit-коммита (SHA и время), и выполнить commit от имени текущего редактора.
- Обновить `PUT /api/documents/{id}`: при изменении опубликованного документа зафиксировать итоговый снимок (content и атрибуты) одним коммитом, затем сохранить связанную запись версии.
- Добавить обработку ошибок и unit/integration-тесты для контента, только атрибутов, Git- и DB-ошибок.

## Capabilities

### New Capabilities

- `git-commit-on-document-save`: Сохранение изменённого опубликованного документа создаёт отдельный Git-коммит и запись с `gitHash`, автором, комментарием и временем.

### Modified Capabilities

- *(none — контракт `PUT /api/documents/{id}` не меняется; меняется его поведение версионирования)*

## Impact

- `DocumentService`, `DocumentContentRepository`, JGit infrastructure adapter и новый persistence adapter версий.
- Существующий Liquibase changeSet `008-create-versions-table.xml`; дополнительная миграция только при выявленном пробеле схемы.
- Backend-тесты и статус уже существующего `PUT /api/documents/{id}` в BACKTRACKER.

**Входные документы:** `../../Docs/documents/backlog-descriptions/E2 Жизненный цикл и Версионность (Lifecycle & Versioning)/F2.2 Контроль версий (Git Integration)/US2.2.1 Git commit при сохранении/description.md`; `../../Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.1 Управление пользователями (RBAC)/US4.1.1 Инициализация проекта/description.md`; `BACKTRACKER.md`; `backend/src/main/resources/db/changelog/changes/008-create-versions-table.xml`.

**Feature Registry:** добавить `git-commit-on-document-save` со статусом `planned`.
