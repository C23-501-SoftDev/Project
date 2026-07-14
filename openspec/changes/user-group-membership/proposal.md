## Why

После реализации управления группами ([`user-group-management`](../user-group-management/proposal.md), US4.1.8) необходимо дать администратору возможность управлять составом групп. Через членство в группе пользователь наследует права доступа, назначенные группе, что и является основной целью группировки. Таблица `user_group_members` уже подготовлена миграцией Liquibase (changelog 015).

Эта задача реализует вторую половину фичи — управление членством в группах (US4.1.9).

## What Changes

- Добавление доменной модели `UserGroupMember` и интерфейса репозитория `UserGroupMemberRepository`.
- Реализация JPA-инфраструктуры (entity, mapper, Spring Data репозиторий) поверх существующей таблицы `user_group_members`.
- Реализация `UserGroupMembershipService` (добавление/удаление участника, получение состава) с проверками существования группы, пользователя и уникальности членства.
- Новые административные эндпоинты под `/api/admin/groups/{groupId}/members` (только для ADMIN).
- Обработка ошибки «членство не найдено» (404) через новое доменное исключение `GroupMembershipNotFoundException`.
- Удаление группы теперь явно отзывает членство всех участников (детерминированно, независимо от FK-каскада СУБД).

## Capabilities

### New Capabilities
- `user-group-membership`: Управление составом групп — добавление и удаление пользователей, просмотр состава.

### Modified Capabilities
- `user-group-management`: Удаление группы дополнительно очищает её состав на уровне сервиса.

## Impact

- **API**: Новые эндпоинты в `AdminGroupController` (`/api/admin/groups/{groupId}/members`).
- **Domain**: Новая сущность `UserGroupMember`, репозиторий `UserGroupMemberRepository`, исключение `GroupMembershipNotFoundException`.
- **Application**: Новый сервис `UserGroupMembershipService`; правка `UserGroupService.deleteGroup`.
- **Infrastructure**: `UserGroupMemberJpaEntity`, `UserGroupMemberJpaMapper`, `UserGroupMemberJpaRepository`, `UserGroupMemberRepositoryImpl`.
- **Interfaces**: DTO `AddGroupMemberRequest`, `GroupMemberResponse`; метод `toGroupMemberResponse` в `RestDtoMapper`; обработчик в `GlobalExceptionHandler`.
- **DB**: Без изменений — используется существующая таблица `user_group_members` (changelog 015).
- **Docs**: Ссылка на [US4.1.9 Управление членством в группах](../../../Docs/documents/backlog-descriptions/E4%20Администрирование%20и%20Безопасность%20(Admin%20&%20Security)/F4.1%20Управление%20пользователями%20(RBAC)/US4.1.9%20Управление%20членством%20в%20группах/description.md). Обновлён `BACKTRACKER.md`.
- **Feature ID**: `user-group-membership`
