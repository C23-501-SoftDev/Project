## Why

Для упрощения администрирования доступа в системе требуется механизм группировки пользователей. Группы позволяют массово назначать права на пространства, вместо того чтобы выдавать их каждому пользователю по отдельности. Таблицы `user_groups` и `user_group_members` уже подготовлены миграциями Liquibase (changelog 014/015), но отсутствует серверная логика управления группами.

Эта задача реализует первую половину фичи — CRUD сущности `Group` (US4.1.8). Управление составом групп (членством) выделено в отдельное изменение [`user-group-membership`](../user-group-membership/proposal.md) (US4.1.9).

## What Changes

- Добавление доменной модели `UserGroup` и интерфейса репозитория `UserGroupRepository`.
- Реализация JPA-инфраструктуры (entity, mapper, Spring Data репозиторий) поверх существующей таблицы `user_groups`.
- Реализация `UserGroupService` с бизнес-логикой создания, чтения, обновления и удаления групп, включая проверку уникальности названия.
- Новые административные эндпоинты CRUD под префиксом `/api/admin/groups` (только для ADMIN).
- Обработка ошибки «группа не найдена» (404) через новое доменное исключение `GroupNotFoundException`.

## Capabilities

### New Capabilities
- `user-group-management`: Полный набор операций управления группами пользователей для администраторов, включая валидацию уникальности названия группы.

## Impact

- **API**: Новые эндпоинты в `AdminGroupController` под префиксом `/api/admin/groups`.
- **Domain**: Новая сущность `UserGroup`, репозиторий `UserGroupRepository`, исключение `GroupNotFoundException`.
- **Application**: Новый сервис `UserGroupService`.
- **Infrastructure**: `UserGroupJpaEntity`, `UserGroupJpaMapper`, `UserGroupJpaRepository`, `UserGroupRepositoryImpl`.
- **Interfaces**: DTO `CreateGroupRequest`, `UpdateGroupRequest`, `GroupResponse`; метод `toGroupResponse` в `RestDtoMapper`; обработчик в `GlobalExceptionHandler`.
- **DB**: Без изменений — используется существующая таблица `user_groups` (changelog 014).
- **Docs**: Ссылка на [US4.1.8 Создание и управление группами](../../../Docs/documents/backlog-descriptions/E4%20Администрирование%20и%20Безопасность%20(Admin%20&%20Security)/F4.1%20Управление%20пользователями%20(RBAC)/US4.1.8%20Создание%20и%20управление%20группами/description.md). Обновлён `BACKTRACKER.md`.
- **Feature ID**: `user-group-management`
