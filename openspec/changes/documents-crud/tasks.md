## 1. Database & Domain

- [x] 1.1 Создать Liquibase changelog для таблицы `documents`
- [x] 1.2 Создать JPA сущность `Document` в `domain.model`
- [x] 1.3 Создать интерфейс `DocumentRepository` в `domain.repository`
- [x] 1.4 Создать интерфейс `DocumentContentRepository` (для Git) в `domain.repository`
- [x] 1.5 Добавить необходимые исключения (DocumentNotFoundException) в `domain.exception`

## 2. Infrastructure Layer (Git Integration)

- [x] 2.1 Реализовать `JGitDocumentContentRepository` в `infrastructure`
- [x] 2.2 Настроить конфигурацию пути к Git-репозиторию в `application.yml`
- [x] 2.3 Написать интеграционный тест для `JGitDocumentContentRepository`

## 3. Application Layer

- [x] 3.1 Создать `DocumentDto` и `CreateDocumentRequest` в `application.dto` (или `interfaces.dto`)
- [x] 3.2 Реализовать `DocumentService` для координации БД и Git
- [x] 3.3 Реализовать маппинг между сущностью и DTO

## 4. Interfaces Layer (REST API)

- [x] 4.1 Создать `DocumentController` с эндпоинтами GET, POST, PUT, DELETE
- [x] 4.2 Настроить права доступа в SecurityConfig (роль EDITOR для мутирующих операций)
- [x] 4.3 Проверить работу API через Swagger UI

## 5. Verification & Documentation

- [x] 5.1 Обновить `BACKTRACKER.md` — актуализировать эндпоинты `/api/documents/**` и их статусы
- [x] 5.2 Проверка критериев качества согласно `openspec/quality-gates.md`
- [x] 5.3 Выполнить юнит-тесты и интеграционные тесты для всей фичи
