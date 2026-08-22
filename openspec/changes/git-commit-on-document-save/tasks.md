## 1. Domain and persistence

- [x] 1.1 Сопоставить `DocumentVersion` с полями существующего `008-create-versions-table.xml`.
- [x] 1.2 Создать domain-модель версии и `DocumentVersionRepository`.
- [x] 1.3 Ввести `GitCommitResult` и обновить `DocumentContentRepository`, чтобы Git-операция возвращала SHA и время.
- [x] 1.4 Реализовать JPA entity, mapper и adapter для `versions`.
- [x] 1.5 Liquibase changeSet не добавлен: существующая таблица уже содержит необходимые поля и ограничения.

## 2. Git commit

- [x] 2.1 Реализовать безопасную запись Markdown, staging конкретного пути и JGit commit с `PersonIdent` текущего редактора.
- [x] 2.2 Получать SHA/время из `RevCommit`; не читать глобальный HEAD.
- [x] 2.3 Формировать совместимый снимок из content и атрибутов (`title`, `status`, `parentId`), включая сохранение только атрибутов.
- [x] 2.4 Для переименования удалить старый и добавить новый путь в одном commit.
- [x] 2.5 Исключить пустые коммиты, path traversal и гонки между сохранениями.

## 3. Application workflow

- [x] 3.1 Обновить `DocumentService.updateDocument`: подготовить итоговый снимок и определить текущего редактора.
- [x] 3.2 При изменении опубликованного документа создать ровно один Git-коммит и `DocumentVersion` с SHA, author, comment и временем.
- [x] 3.3 При Git-ошибке не сохранять документ и метаданные версии.
- [x] 3.4 При DB-ошибке после commit откатить БД, залогировать orphan SHA и вернуть корректную ошибку.
- [x] 3.5 Сохранить REST-контракт `PUT /api/documents/{id}` и не использовать автора `System`.

## 4. Tests

- [x] 4.1 Integration: сохранение контента создаёт один Git-вызов и одну версию с правильными SHA, автором и временем.
- [x] 4.2 Integration: изменение только атрибутов создаёт снимок и коммит.
- [x] 4.3 Unit: Git-ошибка не создаёт DB-версию; DB-ошибка журналирует orphan SHA.
- [x] 4.4 Integration: временный JGit repo содержит ожидаемый файл, автора и SHA коммита.
- [x] 4.5 Persistence/integration: строка `versions` сохраняется в H2 с корректными полями.

## 5. Documentation and verification

- [x] 5.1 Обновить `BACKTRACKER.md` — актуализировать `PUT /api/documents/{id}` и дату в шапке.
- [ ] 5.2 Проверка критериев качества — выполнить все шаги из `openspec/quality-gates.md`: `mvn clean compile`, `mvn test` и, при доступной PostgreSQL, smoke-check `/actuator/health`.
