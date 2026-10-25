## 1. Database & Domain

- [x] 1.1 Создать доменную модель `Attachment`
- [x] 1.2 Создать интерфейсы `AttachmentRepository` и `AttachmentFileStorageRepository`
- [x] 1.3 Добавить доменные исключения для вложений

## 2. Infrastructure Layer

- [x] 2.1 Реализовать JPA entity/repository/mapper для `attachments`
- [x] 2.2 Реализовать файловое сохранение и удаление вложений в blob-хранилище
- [x] 2.3 Настроить лимиты размера и допустимые расширения

## 3. Application Layer

- [x] 3.1 Создать `AttachmentService`
- [x] 3.2 Добавить валидацию размера и типа файла
- [x] 3.3 Реализовать компенсацию при ошибках записи файла или метаданных

## 4. Interfaces Layer

- [x] 4.1 Создать REST API для списка, загрузки, скачивания и удаления вложений
- [x] 4.2 Обновить страницы документа для drag-and-drop и выбора файла
- [x] 4.3 Добавить отображение списка вложений и удаление из UI
- [x] 4.4 Добавить скачивание вложений по ссылке и защиту по правам READ

## 5. Verification & Documentation

- [x] 5.1 Обновить `BACKTRACKER.md`
- [x] 5.2 Проверка качества согласно `openspec/quality-gates.md`
- [x] 5.3 Выполнить `mvn clean compile` и `mvn test`

## Reconciliation evidence (2026-10-25)
Implementation and HTTP contracts were checked against current source and BACKTRACKER.md. Maven clean verify: 127 tests, 0 failures/errors/skips; Node: 3 passed; full Playwright on a fresh PostgreSQL 18.6 test schema: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0. Health endpoint: UP. See docs/PROJECT_REVIEW_RU.md and tests/E2E/E2E_RESULTS.md. This records verification of existing implementation, not a claim that it was first implemented today.
