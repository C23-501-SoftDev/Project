## 1. Подготовка сервисного слоя

- [x] 1.1 Добавить метод `getVisibleHierarchyForSpace(spaceId, userId, isAdmin)` в `DocumentService`
- [x] 1.2 Реализовать логику построения дерева документов в памяти

## 2. Обновление контроллера

- [x] 2.1 Добавить `PageController.spaceDocumentTree` для отдельной загрузки иерархии
- [x] 2.2 Добавить `model.addAttribute("nodes", ...)` для передачи структуры в шаблон

## 3. Обновление UI (Thymeleaf)

- [x] 3.1 Создать фрагмент для рекурсивного отображения дерева документов
- [x] 3.2 Загружать фрагмент из `layout.html` через `main.js` при раскрытии пространства

## 4. Верификация

- [x] 4.1 Проверка критериев качества (см. `openspec/quality-gates.md`)
- [x] 4.2 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы

## Reconciliation evidence (2026-10-25)
Implementation and HTTP contracts were checked against current source and BACKTRACKER.md. Maven clean verify: 127 tests, 0 failures/errors/skips; Node: 3 passed; full Playwright on a fresh PostgreSQL 18.6 test schema: 149 passed, 0 failed/flaky/skipped, workers=1, retries=0. Health endpoint: UP. See docs/PROJECT_REVIEW_RU.md and tests/E2E/E2E_RESULTS.md. This records verification of existing implementation, not a claim that it was first implemented today.
