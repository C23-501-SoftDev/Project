## ADDED Requirements

### Requirement: Document Entity Management
Система ДОЛЖНА поддерживать жизненный цикл сущности Document, включая создание, чтение, обновление и удаление.

#### Scenario: Successful document creation
- **WHEN** пользователь с ролью Editor отправляет POST запрос на `/api/documents` с валидными данными (title, spaceId, content, status)
- **THEN** система создает запись в БД с указанным в запросе статусом (например, `Draft` или `Published`)

#### Scenario: Read document with content
- **WHEN** пользователь запрашивает документ по ID через GET `/api/documents/{id}`
- **THEN** система возвращает метаданные из БД и актуальное содержимое из Git

#### Scenario: Update document content and metadata
- **WHEN** пользователь отправляет PUT запрос на `/api/documents/{id}` с обновленным заголовком или контентом
- **THEN** система обновляет метаданные в БД
- **AND** создает новый коммит в Git с изменениями контента

#### Scenario: Delete document
- **WHEN** пользователь отправляет DELETE запрос на `/api/documents/{id}`
- **THEN** система меняет статус документа на `Deleted`
- **AND** перемещает соответствующий файл в Git в директорию `.archive/`
- **AND** возвращает статус 204 No Content

### Requirement: Git Integration for Content
Система ДОЛЖНА использовать Git в качестве хранилища для тела документа (markdown).

#### Scenario: Content synchronization
- **WHEN** метаданные документа создаются в БД
- **THEN** в Git-репозитории создается файл, путь к которому связан с ID документа
- **AND** любые изменения контента через API автоматически фиксируются коммитом в Git
