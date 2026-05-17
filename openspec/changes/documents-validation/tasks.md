## 1. Domain & Infrastructure Setup

- [ ] 1.1 Создать `DocumentValidationException` в `com.knowledgebase.domain.exception`.
- [ ] 1.2 Добавить метод `existsByTitleAndSpaceIdAndParentId` в `DocumentRepository` и его JPA реализацию.
- [ ] 1.3 Реализовать метод `findAncestors(Long documentId)` в репозитории для проверки циклов.

## 2. Core Validation Logic

- [ ] 2.1 Реализовать приватный метод `validateHierarchy` в `DocumentService` (проверка Space ID родителя и отсутствия циклов).
- [ ] 2.2 Реализовать проверку уникальности заголовка на уровне вложенности в `DocumentService`.
- [ ] 2.3 Внедрить вызовы валидации в методы `createDocument` и `updateDocument`.
- [ ] 2.4 Обновить `GlobalExceptionHandler` для маппинга `DocumentValidationException` в HTTP 422.

## 3. DTO & API Improvements

- [ ] 3.1 Добавить аннотации `@NotBlank` и `@Size` в `CreateDocumentRequest` и `UpdateDocumentRequest`.
- [ ] 3.2 Обновить `BACKTRACKER.md` — актуализировать статусы и эндпоинты документов (учесть код 422).

## 4. Verification & Quality

- [ ] 4.1 Написать интеграционный тест `DocumentValidationIntegrationTest` для проверки всех негативных сценариев (циклы, дубликаты заголовков, чужой Space).
- [ ] 4.2 Запустить существующие тесты `documents-crud` для проверки отсутствия регрессии.
- [ ] 4.3 Проверка критериев качества согласно [openspec/quality-gates.md](../../quality-gates.md).
