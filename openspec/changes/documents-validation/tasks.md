## 1. Domain & Infrastructure Setup

- [x] 1.1 Создать `DocumentValidationException` в `com.knowledgebase.domain.exception`.
- [x] 1.2 Добавить метод `existsByTitleAndSpaceIdAndParentId` в `DocumentRepository` и его JPA реализацию.
- [x] 1.3 Реализовать метод `findAncestors(Long documentId)` в репозитории для проверки циклов.

## 2. Core Validation Logic

- [x] 2.1 Реализовать приватный метод `validateHierarchy` в `DocumentService` (проверка Space ID родителя и отсутствия циклов).
- [x] 2.2 Реализовать проверку уникальности заголовка на уровне вложенности в `DocumentService`.
- [x] 2.3 Внедрить вызовы валидации в методы `createDocument` и `updateDocument`.
- [x] 2.4 Обновить `GlobalExceptionHandler` для маппинга `DocumentValidationException` в HTTP 422.

## 3. DTO & API Improvements

- [x] 3.1 Добавить аннотации `@NotBlank` и `@Size` в `CreateDocumentRequest` и `UpdateDocumentRequest`.
- [x] 3.2 Обновить `BACKTRACKER.md` — актуализировать статусы и эндпоинты документов (учесть код 422).

## 4. Verification & Quality

- [x] 4.1 Написать интеграционный тест `DocumentValidationIntegrationTest` для проверки всех негативных сценариев (циклы, дубликаты заголовков, чужой Space).
- [ ] 4.2 Запустить существующие тесты `documents-crud` для проверки отсутствия регрессии.
- [ ] 4.3 Проверка критериев качества согласно [openspec/quality-gates.md](../../quality-gates.md).
