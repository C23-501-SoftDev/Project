## 1. Domain Layer

- [x] 1.1 Добавить методы в `Space` для обновления полей (name, description, ownerId).
- [x] 1.2 Расширить `SpaceRepository` методами `deleteById` и `findById`.

## 2. Infrastructure Layer

- [x] 2.1 Реализовать `findById` и `deleteById` в `SpaceJpaRepository`.
- [x] 2.2 Обновить `SpaceRepositoryImpl` (маппинг и вызов JPA).
- [x] 2.3 Добавить метод `existsByNameAndIdNot` для проверки уникальности при обновлении.

## 3. Application Layer

- [x] 3.1 Создать `UpdateSpaceRequest` DTO.
- [x] 3.2 Реализовать метод `getSpaceById` в `SpaceService`.
- [x] 3.3 Реализовать метод `updateSpace` в `SpaceService` (с логикой автоматической смены OWNER через `PermissionService`).
- [x] 3.4 Реализовать метод `deleteSpace` в `SpaceService`.

## 4. Interfaces Layer (API)

- [x] 4.1 Реализовать эндпоинт `GET /api/admin/spaces/{id}` в `SpaceController`.
- [x] 4.2 Реализовать эндпоинт `PUT /api/admin/spaces/{id}` в `SpaceController`.
- [x] 4.3 Реализовать эндпоинт `DELETE /api/admin/spaces/{id}` в `SpaceController`.

## 5. Verification & Finalization

- [x] 5.1 Написать интеграционные тесты для CRUD операций (Update, Delete, GetById).
- [x] 5.2 Проверить автоматическое обновление прав OWNER при смене владельца в тестах.
- [x] 5.3 Проверка критериев качества согласно `openspec/quality-gates.md`.
- [x] 5.4 Обновить `BACKTRACKER.md` — актуализировать затронутые эндпоинты и их статусы.
