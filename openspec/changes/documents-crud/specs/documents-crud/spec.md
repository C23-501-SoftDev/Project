## ADDED Requirements

### Requirement: Document Entity Management
Система ДОЛЖНА (SHALL) поддерживать жизненный цикл сущности Document, включая создание, чтение, обновление и удаление.

#### Scenario: Successful document creation
- **WHEN** пользователь с ролью Editor отправляет POST запрос на `/api/documents` с валидными данными (title, spaceId, content; parentId/templateId опциональны)
- **THEN** система создает запись в БД со статусом `Draft`; публикация выполняется отдельным `POST /api/documents/{id}/publish`

#### Scenario: Read document with content
- **WHEN** пользователь запрашивает документ по ID через GET `/api/documents/{id}`
- **THEN** система возвращает метаданные из БД и актуальное содержимое из Git

#### Scenario: Update document content and metadata
- **WHEN** пользователь отправляет PUT запрос на `/api/documents/{id}` с обновленным заголовком или контентом
- **THEN** система обновляет метаданные в БД
- **AND** при изменении опубликованного документа создает Git-снимок и запись версии; обычный PUT не меняет статус публикации

#### Scenario: Delete document
- **WHEN** пользователь отправляет DELETE запрос на `/api/documents/{id}`
- **THEN** система меняет статус документа на `Deleted`
- **AND** перемещает соответствующий файл в Git в директорию `.archive/`
- **AND** возвращает статус 204 No Content

### Requirement: Git Integration for Content
Система ДОЛЖНА (SHALL) использовать Git в качестве хранилища для тела документа (markdown).

#### Scenario: Content synchronization
- **WHEN** метаданные документа создаются в БД
- **THEN** в Git-репозитории создается файл, путь к которому связан с ID документа
- **AND** изменённый опубликованный документ сохраняется снимком с Git-коммитом и метаданными версии; изменения черновика не создают опубликованную версию
