## 1. Domain Layer

- [x] 1.1 Создать доменную модель `UserGroup` (фабрики `create`/`restore`, метод `update`).
- [x] 1.2 Создать интерфейс репозитория `UserGroupRepository`.
- [x] 1.3 Создать исключение `GroupNotFoundException` (наследник `DomainException`).

## 2. Infrastructure Layer

- [x] 2.1 Создать JPA-сущность `UserGroupJpaEntity` (таблица `user_groups`).
- [x] 2.2 Создать маппер `UserGroupJpaMapper`.
- [x] 2.3 Создать Spring Data репозиторий `UserGroupJpaRepository` (`findByName`, `existsByName`, `existsByNameAndIdNot`).
- [x] 2.4 Реализовать `UserGroupRepositoryImpl`.

## 3. Application Layer

- [x] 3.1 Реализовать `UserGroupService` (`createGroup`, `getGroupById`, `getAllGroups`, `countGroups`, `updateGroup`, `deleteGroup`) с проверкой уникальности имени.

## 4. Interfaces Layer (API)

- [x] 4.1 Создать DTO `CreateGroupRequest`, `UpdateGroupRequest`, `GroupResponse`.
- [x] 4.2 Добавить `toGroupResponse` в `RestDtoMapper`.
- [x] 4.3 Зарегистрировать обработчик `GroupNotFoundException` (404) в `GlobalExceptionHandler`.
- [x] 4.4 Реализовать `AdminGroupController` (GET list, GET by id, POST, PUT, DELETE) под `/api/admin/groups`.

## 5. Verification & Finalization

- [x] 5.1 Написать интеграционные тесты `UserGroupManagementIntegrationTest` (create, list, get, update, delete, conflict, validation, 404, 403).
- [x] 5.2 Проверка критериев качества согласно `openspec/quality-gates.md`.
- [x] 5.3 Обновить `BACKTRACKER.md` — добавить эндпоинты `/api/admin/groups`.
- [x] 5.4 Обновить `openspec/feature-registry.json` — добавить запись `user-group-management`.
