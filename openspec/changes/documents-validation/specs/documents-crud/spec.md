## MODIFIED Requirements

### Requirement: Document Entity Management
Система ДОЛЖНА поддерживать жизненный цикл сущности Document, включая создание, чтение, обновление и удаление, с обязательным применением бизнес-валидации при сохранении.

#### Scenario: Successful document creation
- **WHEN** пользователь с ролью Editor отправляет POST запрос на `/api/documents` с валидными данными (title, spaceId, content, status)
- **THEN** система создает запись в БД с указанным в запросе статусом (например, `Draft` или `Published`)

#### Scenario: Validation failure on creation
- **WHEN** пользователь отправляет некорректные данные (пустой заголовок, слишком длинный заголовок > 500 симв.)
- **THEN** система возвращает 400 Bad Request с перечислением ошибок полей

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
