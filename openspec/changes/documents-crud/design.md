## Context

В системе реализованы пользователи и пространства. Теперь необходимо добавить сущность "Документ", которая является основным носителем контента. Особенность реализации заключается в гибридном хранении: метаданные (ID, заголовок, автор, ID пространства, статус) хранятся в PostgreSQL, а само тело документа (Markdown) — в Git-репозитории для обеспечения версионности в будущем.

## Goals / Non-Goals

**Goals:**
- Реализовать CRUD для документов с интеграцией JGit.
- Обеспечить разделение ответственности согласно Чистой Архитектуре.
- Настроить хранение контента в структурированном виде в Git (например, `docs/{spaceId}/{documentId}.md`).
- Реализовать REST API эндпоинты для фронтенда.

**Non-Goals:**
- Реализация полной истории версий (Git history API) — будет в следующих задачах.
- Сложная логика разрешений (только базовая привязка к Editor роли).
- Полнотекстовый поиск по содержимому документов.

## Decisions

### 1. Схема данных (Database)
- Добавление таблицы `documents`: `id (UUID)`, `title (VARCHAR)`, `space_id (UUID)`, `author_id (UUID)`, `status (VARCHAR)`, `created_at`, `updated_at`.
- Поле `content_path` (или вычисляемый путь) для связи с файлом в Git.

### 2. Интеграция с Git (Infrastructure Layer)
- Использование библиотеки **JGit**.
- Сервис `GitDocumentStorage` в слое `infrastructure`, реализующий интерфейс `DocumentContentRepository` из `domain`.
- При каждом сохранении/обновлении: `git add`, `git commit`.

### 3. Слой приложения (Application Layer)
- `DocumentService` будет координировать работу с `DocumentRepository` (JPA) и `DocumentContentRepository` (Git).
- Использование DTO для передачи данных между слоями.

### 4. REST API (Interfaces Layer)
- `DocumentController` с эндпоинтами:
    - `POST /api/documents` (Create)
    - `GET /api/documents/{id}` (Read)
    - `PUT /api/documents/{id}` (Update)
    - `DELETE /api/documents/{id}` (Delete)
    - `GET /api/documents` (List by space)

## Risks / Trade-offs

- **[Risk]** Рассинхрон между БД и Git (например, транзакция в БД прошла, а Git commit упал).
    - **Mitigation** Реализация транзакционного механизма или компенсационных действий (retry/cleanup).
- **[Risk]** Конфликты в Git при конкурентном доступе.
    - **Mitigation** Использование блокировок на уровне приложения для редактирования одного документа.

## Proposed Code Changes

- **Domain**: `Document.java`, `DocumentRepository.java`, `DocumentContentRepository.java`.
- **Application**: `DocumentService.java`, `DocumentMapper.java`, `DocumentDto.java`.
- **Infrastructure**: `JpaDocumentRepository.java`, `JGitDocumentContentRepository.java`.
- **Interfaces**: `DocumentController.java`.
- **Resources**: Liquibase changelog для таблицы `documents`.
