## Context

`StorageConfig` уже инициализирует локальный Git-репозиторий, а JGit подключён. `DocumentService.updateDocument` сохраняет Markdown через `DocumentContentRepository`, но передаёт автора `System`, не получает SHA и не создаёт запись в `versions`. Сохранение только title/status/parent может не затронуть файл и не создать коммит. REST-контракт `PUT /api/documents/{id}` менять не требуется.

## Goals / Non-Goals

**Goals:**

- При успешном изменении опубликованного документа создать ровно один Git-коммит с его итоговым снимком.
- Сохранить `gitHash`, `authorId`, `comment`, `createdAt` в PostgreSQL и связать их с документом.
- Версионировать content и изменяемые атрибуты; использовать аутентифицированного редактора как Git-автора.
- Не создавать DB-метаданные, если Git-коммит не создан.

**Non-Goals:**

- Просмотр истории, diff, restore, push в remote, вложения, удаление и версионирование черновиков.
- Изменение request/response API.

## Decisions

### 1. Git-порт возвращает результат коммита

`DocumentContentRepository` получает value object `GitCommitResult(hash, committedAt)`. JGit-адаптер безопасно записывает итоговый Markdown внутри корня репозитория, stage'ит только пути документа и вызывает `commit` с `PersonIdent(editor.login, editor.email)`. SHA возвращается из `RevCommit`, а не читается через общий `HEAD`, чтобы исключить гонку с другим сохранением.

### 2. Каждый снимок включает содержимое и атрибуты

Application service формирует Markdown-снимок из актуальных `title`, `status`, `parentId` и content (например, через совместимый YAML front matter). Если изменены только атрибуты, сервис читает текущее content, записывает новый снимок и коммитит его. Переименование stage'ит удаление старого пути и добавление нового в рамках одного коммита.

### 3. Порядок и согласованность

После валидации Git-коммит выполняется до сохранения `DocumentVersion`; поэтому ошибка Git не подтверждает изменения в БД и не создаёт строку `versions`. При ошибке БД после Git-коммита транзакция БД откатывается, orphan SHA логируется для ручного восстановления; Git reset не применяется, поскольку он небезопасен при параллельных коммитах. Операции JGit для одного локального репозитория должны быть сериализованы lock'ом.

### 4. Отсутствие изменения

Пустой Git-коммит не создаётся. Требование охватывает сохранение изменённого документа: если после сравнения нет изменения ни content, ни версионируемых атрибутов, endpoint возвращает текущий документ без новой версии.

## Affected Code

| Layer | Changes |
| --- | --- |
| `domain/model`, `domain/repository` | `DocumentVersion`, `GitCommitResult`, новый порт версий и расширенный Git-порт. |
| `application/service/DocumentService` | Подготовка снимка, orchestration Git → version metadata, ошибки. |
| `infrastructure` | JGit save/stage/rename/commit с path validation и JPA adapter для `versions`. |
| `db` | Использование changeSet 008; новый Liquibase changeSet только для реально недостающей схемы. |
| `interfaces/rest` | API без изменений; корректное отображение ошибки сохранения. |

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| Нет общей транзакции Git/PostgreSQL | Git перед DB, rollback DB и журнал orphan SHA. |
| Параллельные сохранения | Lock на локальный Git-репозиторий; SHA берётся из `RevCommit`. |
| Path traversal | Нормализация пути и проверка, что он остаётся внутри repo root. |
| Существующие Markdown-файлы | Обратная совместимость front matter проверяется тестами. |
