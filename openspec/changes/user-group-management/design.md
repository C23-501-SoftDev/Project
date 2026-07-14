## Context

В системе уже реализовано управление пользователями (`admin-users-crud`) и пространствами (`space-crud`) по чистой архитектуре. Схема БД для групп (`user_groups`, `user_group_members`) подготовлена миграциями Liquibase 014/015, но серверный код отсутствует. Требуется реализовать управление сущностью `Group`, строго следуя сложившимся слоям (domain / application / infrastructure / interfaces).

## Goals / Non-Goals

**Goals:**
- Реализовать полный CRUD для `UserGroup` на уровне API администратора.
- Гарантировать уникальность названия группы при создании и обновлении.
- Переиспользовать существующие паттерны (фабричные методы домена, мапперы, `PageResponse`, `GlobalExceptionHandler`).

**Non-Goals:**
- Управление составом групп (добавление/удаление участников) — отдельное изменение `user-group-membership` (US4.1.9).
- Назначение прав группам на пространства (`space_group_permissions`) — отдельная user story US4.2.3.
- UI администратора (только бэкенд API).

## Decisions

### 1. Использование существующей схемы БД
**Решение:** Не создавать новые миграции — таблица `user_groups` уже описана в changelog 014.
**Обоснование:** JPA-сущность `UserGroupJpaEntity` отображается на существующую таблицу; в dev/prod схему создаёт Liquibase, в тестах — Hibernate `ddl-auto: create-drop`.

### 2. Удаление группы — hard delete
**Решение:** Группа удаляется физически (`DELETE`). Связанные записи `user_group_members` и `space_group_permissions` удаляются каскадно на уровне БД (`ON DELETE CASCADE`, changelog 015/016). Пользователи не затрагиваются.
**Обоснование:** Соответствует US4.1.8 Сценарий 2 («права, зависящие от группы, отзываются») и модели данных (`120.data-model.md`). Soft-delete для групп не предусмотрен.

### 3. Схема API
- `GET /api/admin/groups?page&size` — список групп (`PageResponse<GroupResponse>`).
- `GET /api/admin/groups/{groupId}` — детали группы.
- `POST /api/admin/groups` — создание (`CreateGroupRequest`).
- `PUT /api/admin/groups/{groupId}` — обновление (`UpdateGroupRequest`).
- `DELETE /api/admin/groups/{groupId}` — удаление (204 No Content).

Доступ только для ADMIN — двойная защита: `SecurityConfig` (`/api/admin/**`) и `@PreAuthorize("hasRole('ADMIN')")` на контроллере.

### 4. Изменения в слоях
- **Domain**: `UserGroup` (create/restore/update), `UserGroupRepository`, `GroupNotFoundException`.
- **Application**: `UserGroupService` (`@Transactional`).
- **Infrastructure**: `UserGroupJpaEntity`, `UserGroupJpaMapper`, `UserGroupJpaRepository`, `UserGroupRepositoryImpl`.
- **Interfaces**: `AdminGroupController`, DTO, расширение `RestDtoMapper` и `GlobalExceptionHandler`.

## Risks / Trade-offs

- **[Risk]** Дублирование имён при обновлении. → **Mitigation**: валидация через `existsByNameAndIdNot(name, id)`.
- **[Risk]** Каскадное удаление членства полагается на FK БД, которого нет в тестовой H2-схеме (колонки описаны как простые `Long` без JPA-связей). → **Mitigation**: в рамках US4.1.9 удаление группы дополнительно очищает состав на уровне сервиса, что делает поведение детерминированным независимо от наличия FK.
