## 1. Domain Layer

- [x] 1.1 Создать доменную модель `UserGroupMember` (фабрики `create`/`restore`).
- [x] 1.2 Создать интерфейс репозитория `UserGroupMemberRepository`.
- [x] 1.3 Создать исключение `GroupMembershipNotFoundException` (наследник `DomainException`).

## 2. Infrastructure Layer

- [x] 2.1 Создать JPA-сущность `UserGroupMemberJpaEntity` (таблица `user_group_members`, уникальность group_id+user_id).
- [x] 2.2 Создать маппер `UserGroupMemberJpaMapper`.
- [x] 2.3 Создать Spring Data репозиторий `UserGroupMemberJpaRepository`.
- [x] 2.4 Реализовать `UserGroupMemberRepositoryImpl`.

## 3. Application Layer

- [x] 3.1 Реализовать `UserGroupMembershipService` (`addMember`, `removeMember`, `getMembers`) с проверками 404/409.
- [x] 3.2 Доработать `UserGroupService.deleteGroup` — явно очищать состав группы.

## 4. Interfaces Layer (API)

- [x] 4.1 Создать DTO `AddGroupMemberRequest`, `GroupMemberResponse`.
- [x] 4.2 Добавить `toGroupMemberResponse` в `RestDtoMapper`.
- [x] 4.3 Зарегистрировать обработчик `GroupMembershipNotFoundException` (404) в `GlobalExceptionHandler`.
- [x] 4.4 Добавить эндпоинты членства в `AdminGroupController` (GET/POST/DELETE `/api/admin/groups/{groupId}/members`).

## 5. Verification & Finalization

- [x] 5.1 Написать интеграционные тесты `UserGroupMembershipIntegrationTest` (add, list, remove, 404, 409, каскад при удалении группы, 403).
- [x] 5.2 Проверка критериев качества согласно `openspec/quality-gates.md`.
- [x] 5.3 Обновить `BACKTRACKER.md` — добавить эндпоинты `/api/admin/groups/{groupId}/members`.
- [x] 5.4 Обновить `openspec/feature-registry.json` — добавить запись `user-group-membership`.
