# Отчёт по фичам

> Дата: 2026-07-20. Ветка: `feature/email-notifications`.
>
> Покрываемые пункты бэклога:
>
> | US | Название | Статус | Тесты |
> |----|----------|--------|-------|
> | US4.1.5 | Логирование действий системы (аудит) | ✅ работает | `AuditLogIntegrationTest` (4) |
> | US4.1.7* | Управление группами пользователей | ✅ работает | `GroupIntegrationTest` (3) |
> | US4.1.9 | Управление членством в группах | ✅ работает | `GroupIntegrationTest`, `GroupPermissionIntegrationTest` (4) |
> | US4.2.1 | CRUD сущности Space | ✅ работает | `SpaceIntegrationTest` (4) |
> | US4.2.2 | Назначение прав на Пространства (пользователи + группы) | ✅ работает | `PermissionIntegrationTest` (3), `GrantPermissionTest` (2), `GroupPermissionIntegrationTest` (4) |
> | US4.3.1 | Рассылка Email-уведомлений | ✅ работает | `NotificationIntegrationTest` (6) |
>
> \* **Примечание по нумерации:** в репозитории Docs «Управление группами пользователей» числится как **US4.1.8** («Создание и управление группами»), а US4.1.7 — это «Soft-удаление пользователей» (реализовано ранее). В отчёте фича групп описана под формулировкой из задачи.

---

## 0. Как всё запустить

### Поднять окружение

```bash
docker compose --env-file .env up -d --build   # или make dev-up
# Приложение: http://localhost:8080  (Swagger: http://localhost:8080/swagger-ui.html)
# Дефолтный админ: login=admin, password=admin123
```

### Запустить ВСЕ тесты

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test
```

Ожидаемый результат: **58 тестов, 0 падений** (H2 in-memory, PostgreSQL не нужен).
Отчёт покрытия JaCoCo: `backend/target/site/jacoco/index.html`.

### Получить JWT для ручных проверок (нужен во всех curl ниже)

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"admin123"}'
# из заголовка Set-Cookie взять значение JWT=<token> и подставлять его ниже
```

Все админ-эндпоинты требуют cookie `JWT` пользователя с `is_admin = true`.

---

## 1. US4.1.5 — Логирование действий системы (аудит)

### Что делает

Каждое критическое действие пользователя записывается в таблицу `audit_log`:
кто (`user_id`, `user_login`), когда (`created_at`), что сделал (`action_type`),
над чем (`resource_type`, `resource_id`), детали (`details` — например, старая/новая роль)
и с какого адреса (`ip_address`). Журнал доступен только администраторам, с фильтрами
по пользователю, типу действия и диапазону дат.

Журналируемые действия: документы (`DOCUMENT_CREATED/UPDATED/DELETED/HARD_DELETED/RESTORED`),
пространства (`SPACE_CREATED/UPDATED/DELETED/HARD_DELETED/RESTORED`),
пользователи (`USER_CREATED/UPDATED/DELETED/RESTORED/PASSWORD_CHANGED`),
права (`PERMISSION_GRANTED/REVOKED`, `GROUP_PERMISSION_GRANTED/REVOKED`),
группы (`GROUP_CREATED/UPDATED/DELETED`, `GROUP_MEMBER_ADDED/REMOVED`).

### Код

| Слой | Файл |
|------|------|
| Миграция | `backend/src/main/resources/db/changelog/changes/029-create-audit-log-table.xml` |
| Domain | `backend/src/main/java/com/knowledgebase/domain/model/AuditLogEntry.java`, `domain/repository/AuditLogRepository.java` |
| Infrastructure | `infrastructure/persistence/entity/AuditLogJpaEntity.java`, `.../repository/AuditLogJpaRepository.java`, `AuditLogRepositoryImpl.java` |
| Application | `application/service/AuditService.java` |
| REST | `interfaces/rest/controller/AuditLogController.java`, `dto/response/AuditLogResponse.java` |

Ключевой фрагмент — `AuditService.record()` (вызывается из `UserService`, `SpaceService`, `DocumentService`, `GroupService`):

```java
public void record(String actionType, String resourceType, Long resourceId, String details) {
    try {
        User current = currentUser();               // из SecurityContext
        AuditLogEntry entry = AuditLogEntry.create(
                current != null ? current.getId() : null,
                current != null ? current.getLogin() : "system",
                actionType, resourceType, resourceId, details,
                currentIpAddress());                 // X-Forwarded-For / remoteAddr
        auditLogRepository.save(entry);
    } catch (RuntimeException ex) {
        // Аудит не должен ломать бизнес-операцию
        log.error("Не удалось записать событие аудита {}: {}", actionType, ex.getMessage());
    }
}
```

Важные свойства:
- запись идёт **в той же транзакции**, что и бизнес-операция: если операция откатилась — записи в журнале нет;
- сбой аудита гасится и не ломает основную операцию;
- автор и IP определяются автоматически, сервисам ничего передавать не нужно.

### Ручное тестирование

1. Залогиниться админом, создать пространство:
   ```bash
   curl -X POST http://localhost:8080/api/admin/spaces --cookie "JWT=<token>" \
     -H "Content-Type: application/json" -d '{"name":"audit-demo","description":"x"}'
   ```
2. Открыть журнал:
   ```bash
   curl "http://localhost:8080/api/admin/audit" --cookie "JWT=<token>"
   ```
   → в `content[0]` запись `SPACE_CREATED` с `userLogin: "admin"`, `ipAddress`, временем.
3. Проверить фильтры:
   ```bash
   curl "http://localhost:8080/api/admin/audit?actionType=SPACE_CREATED" --cookie "JWT=<token>"
   curl "http://localhost:8080/api/admin/audit?userId=1&dateFrom=2026-07-20T00:00:00" --cookie "JWT=<token>"
   ```
4. Проверить запрет доступа: повторить п.2 с JWT обычного пользователя (не админа) → **403 Forbidden**.

### Тесты фичи

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test -Dtest=AuditLogIntegrationTest
```

| Тест | Проверяет |
|------|-----------|
| `documentActions_createAuditEntries` | создание/изменение/удаление документа даёт записи `DOCUMENT_*` (сценарий 1 AC) |
| `adminPermissionActions_areAuditedWithDetails` | выдача права и смена роли журналируются со старыми/новыми значениями (сценарий 2 AC) |
| `auditLog_isForbiddenForNonAdmins` | READER/EDITOR получают 403 (сценарий 3 AC) |
| `auditLog_supportsFilteringByUserAndDate` | фильтры userId / actionType / dateFrom-dateTo (сценарий 4 AC) |

---

## 2. US4.1.7 (US4.1.8 в Docs) — Управление группами пользователей

### Что делает

Полный CRUD групп для массового управления доступом: группа имеет уникальное название
и описание; при удалении группы автоматически удаляются все членства и **отзываются все
права группы на пространства** (критерий приёмки, сценарий 2). Все операции — только для админа
и журналируются в аудит.

### Код

| Слой | Файл |
|------|------|
| Миграции (были в проекте) | `changes/014-create-user-groups-table.xml`, `015-create-user-group-members-table.xml` |
| Domain | `domain/model/UserGroup.java`, `domain/repository/UserGroupRepository.java`, `domain/exception/GroupNotFoundException.java` |
| Infrastructure | `infrastructure/persistence/entity/UserGroupJpaEntity.java`, `.../repository/UserGroupJpaRepository.java`, `UserGroupRepositoryImpl.java` |
| Application | `application/service/GroupService.java` |
| REST | `interfaces/rest/controller/GroupController.java`, DTO: `CreateGroupRequest`, `UpdateGroupRequest`, `GroupResponse` |

Ключевой фрагмент — `GroupService.deleteGroup()` (отзыв зависимых прав):

```java
@Transactional
public void deleteGroup(Long groupId) {
    UserGroup group = getGroupById(groupId);                 // 404 если нет
    groupPermissionRepository.deleteByGroupId(groupId);      // отзываем права группы
    memberRepository.deleteByGroupId(groupId);               // удаляем членства
    groupRepository.deleteById(groupId);
    auditService.record("GROUP_DELETED", RESOURCE_GROUP, groupId, "name='" + group.getName() + "'");
}
```

### API

| Метод | Эндпоинт | Ответы |
|-------|----------|--------|
| GET | `/api/admin/groups?page=0&size=50` | 200 (PageResponse, у группы есть `memberCount`) |
| POST | `/api/admin/groups` `{name, description}` | 201; 409 — имя занято; 400 — пустое имя |
| GET | `/api/admin/groups/{id}` | 200; 404 |
| PUT | `/api/admin/groups/{id}` `{name, description}` | 200; 404; 409 |
| DELETE | `/api/admin/groups/{id}` | 204; 404 |

### Ручное тестирование

```bash
# 1. Создать группу → 201
curl -X POST http://localhost:8080/api/admin/groups --cookie "JWT=<token>" \
  -H "Content-Type: application/json" -d '{"name":"Аналитики","description":"BA"}'

# 2. Повторить тот же запрос → 409 Conflict (имя уникально)

# 3. Список групп → группа в content, memberCount=0
curl http://localhost:8080/api/admin/groups --cookie "JWT=<token>"

# 4. Переименовать → 200
curl -X PUT http://localhost:8080/api/admin/groups/1 --cookie "JWT=<token>" \
  -H "Content-Type: application/json" -d '{"name":"Аналитики 2.0","description":"BA"}'

# 5. Удалить → 204; повторный GET /api/admin/groups/1 → 404
curl -X DELETE http://localhost:8080/api/admin/groups/1 --cookie "JWT=<token>"
```

### Тесты фичи

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test -Dtest=GroupIntegrationTest
```

| Тест | Проверяет |
|------|-----------|
| `admin_canManageGroups_CRUD` | создание, 409 на дубликат, чтение, список, обновление, 409 на чужое имя, удаление, 404 после удаления |
| `groupMembers_addListRemove` | членство (см. US4.1.9) |
| `groups_areForbiddenForNonAdmins` | 403 для не-админов |

---

## 3. US4.1.9 — Управление членством в группах

### Что делает

Добавление/удаление пользователей в группы. Участник группы **наследует права группы
на пространства** (см. US4.2.2): добавили в группу — получил доступ, исключили — потерял.

### Код

| Слой | Файл |
|------|------|
| Domain | `domain/model/GroupMember.java`, `domain/repository/GroupMemberRepository.java` |
| Infrastructure | `infrastructure/persistence/entity/UserGroupMemberJpaEntity.java`, `.../repository/UserGroupMemberJpaRepository.java`, `GroupMemberRepositoryImpl.java` |
| Application | `application/service/GroupService.java` (методы `addMember`, `removeMember`, `getMembers`) |
| REST | `interfaces/rest/controller/GroupController.java`, DTO: `AddGroupMemberRequest`, `GroupMemberResponse` |

### API

| Метод | Эндпоинт | Ответы |
|-------|----------|--------|
| GET | `/api/admin/groups/{id}/members` | 200 (userId, login, email, addedAt); 404 |
| POST | `/api/admin/groups/{id}/members` `{userId}` | 201; 404 — нет группы/пользователя; 409 — уже в группе |
| DELETE | `/api/admin/groups/{id}/members/{userId}` | 204; 409 — не состоит в группе |

### Ручное тестирование

```bash
# 1. Добавить пользователя 2 в группу 1 → 201
curl -X POST http://localhost:8080/api/admin/groups/1/members --cookie "JWT=<token>" \
  -H "Content-Type: application/json" -d '{"userId":2}'

# 2. Повторить → 409 (уже состоит)

# 3. Список участников → login/email пользователя
curl http://localhost:8080/api/admin/groups/1/members --cookie "JWT=<token>"

# 4. Удалить из группы → 204; повторное удаление → 409
curl -X DELETE http://localhost:8080/api/admin/groups/1/members/2 --cookie "JWT=<token>"
```

Проверка наследования прав — см. сценарий в разделе US4.2.2.

### Тесты фичи

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test -Dtest="GroupIntegrationTest#groupMembers_addListRemove,GroupPermissionIntegrationTest"
```

| Тест | Проверяет |
|------|-----------|
| `GroupIntegrationTest#groupMembers_addListRemove` | add/list/remove, 404/409-сценарии, memberCount |
| `GroupPermissionIntegrationTest#guest_getsAccessViaGroup_andLosesItOnRevoke` | участник наследует права группы (сценарий 1 AC) |
| `GroupPermissionIntegrationTest#guest_losesAccess_whenRemovedFromGroup` | исключение из группы отзывает доступ (сценарий 2 AC) |

---

## 4. US4.2.1 — CRUD сущности Space

### Что делает

Полный CRUD пространств для администратора: создание с автоматической выдачей права
`OWNER` владельцу, редактирование (включая смену владельца с переносом права OWNER),
**soft-delete** (пространство и его документы помечаются удалёнными, данные сохраняются),
восстановление из корзины. Бизнес-правило из описания US: **владельцем пространства может
быть только пользователь с `is_admin = true`** — иначе `422 Unprocessable Entity`.

### Код

| Слой | Файл |
|------|------|
| Domain | `domain/model/Space.java`, `domain/repository/SpaceRepository.java`, `domain/exception/SpaceValidationException.java` |
| Infrastructure | `infrastructure/persistence/entity/SpaceJpaEntity.java`, `.../repository/SpaceJpaRepository.java`, `SpaceRepositoryImpl.java` |
| Application | `application/service/SpaceService.java` |
| REST | `interfaces/rest/controller/SpaceController.java`, DTO: `CreateSpaceRequest`, `UpdateSpaceRequest`, `SpaceResponse` |

Ключевой фрагмент — валидация владельца (используется в `createSpace` и `updateSpace`):

```java
private void validateOwnerIsAdmin(Long ownerId) {
    User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new UserNotFoundException(ownerId));
    if (!owner.isAdmin()) {
        throw new SpaceValidationException("Владельцем пространства может быть только администратор");
    }
}
```

Порядок soft-delete (сначала документы «от листьев к корням», затем само пространство):

```java
@Transactional
public void deleteSpace(Long spaceId) {
    Space space = spaceRepository.findById(spaceId)
            .orElseThrow(() -> new SpaceNotFoundException(spaceId));
    List<Document> documents = sortChildrenFirst(documentService.getDocumentsInSpace(spaceId, false));
    for (Document doc : documents) {
        documentService.deleteDocument(doc.getId());
    }
    space.softDelete();
    spaceRepository.save(space);
    auditService.record("SPACE_DELETED", RESOURCE_SPACE, spaceId, ...);
}
```

### API

| Метод | Эндпоинт | Ответы |
|-------|----------|--------|
| GET | `/api/admin/spaces?page=&size=&status=active\|deleted\|all` | 200 |
| POST | `/api/admin/spaces` `{name, description, ownerId?}` | 201 (owner по умолчанию — текущий админ); 409 — имя занято; 422 — владелец не админ |
| GET | `/api/admin/spaces/{id}` | 200; 404 (в т.ч. для soft-удалённых) |
| PUT | `/api/admin/spaces/{id}` `{name, description, ownerId}` | 200 (право OWNER переносится); 404; 409; 422 |
| DELETE | `/api/admin/spaces/{id}` | 204 (soft-delete вместе с документами) |
| POST | `/api/admin/spaces/{id}/restore` | 204 (восстановление пространства и его документов) |

### Ручное тестирование

```bash
# 1. Создать → 201, ownerId = id админа
curl -X POST http://localhost:8080/api/admin/spaces --cookie "JWT=<token>" \
  -H "Content-Type: application/json" -d '{"name":"Demo","description":"d"}'

# 2. Дубликат имени → 409
# 3. Сменить владельца на НЕ-админа → 422 с сообщением про администратора
curl -X PUT http://localhost:8080/api/admin/spaces/1 --cookie "JWT=<token>" \
  -H "Content-Type: application/json" -d '{"name":"Demo","description":"d","ownerId":<id не-админа>}'

# 4. Сменить владельца на другого админа → 200;
#    GET /api/admin/spaces/1/permissions → у нового владельца OWNER, у старого OWNER снят

# 5. Удалить → 204; GET /api/admin/spaces/1 → 404;
#    GET /api/admin/spaces?status=deleted → пространство в корзине
curl -X DELETE http://localhost:8080/api/admin/spaces/1 --cookie "JWT=<token>"

# 6. Восстановить → 204; пространство снова в списках
curl -X POST http://localhost:8080/api/admin/spaces/1/restore --cookie "JWT=<token>"
```

Через UI: `http://localhost:8080/admin/spaces` (таблица, создание/редактирование/удаление).

### Тесты фичи

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test -Dtest=SpaceIntegrationTest
```

| Тест | Проверяет |
|------|-----------|
| `admin_canManageSpace_CRUD` | create → get → update (со сменой владельца и переносом OWNER) → 422 для владельца-не-админа → delete → 404 |
| `admin_canCreateSpace_duplicateNameReturns409_andOwnerDefaultsToCurrentUser` | сценарий 1 AC: уникальность имени, владелец по умолчанию |
| `admin_grantsPermission_andUserSeesSpaceInMySpaces` | выданное право открывает пространство пользователю |
| `grantPermission_toNonExistingUser_orSpace_returns404` | 404-сценарии |

---

## 5. US4.2.2 — Механизм назначения прав на Пространства

### Что делает

Назначение прав `READ` / `WRITE` / `OWNER` на пространство **пользователям** (таблица
`space_user_permissions`) и **группам** (таблица `space_group_permissions`). Права работают
поверх глобальных ролей:

| Глобальная роль | Что даёт право на пространство |
|-----------------|-------------------------------|
| `EDITOR` | не требуется — полный доступ везде |
| `READER` | `WRITE` даёт редактирование конкретного пространства |
| `GUEST` | единственный способ получить доступ: `READ` — чтение, `WRITE` — редактирование |

Логика: `WRITE` поглощает `READ`, `OWNER` — оба; повторная выдача → 409. Пользователь
наследует права всех своих групп — проверки доступа (`canRead`/`canWrite`) и список
«мои пространства» учитывают оба источника.

### Код

| Слой | Файл |
|------|------|
| Domain | `domain/model/SpacePermission.java`, `SpaceGroupPermission.java`, `PermissionType.java`; порты `SpacePermissionRepository`, `SpaceGroupPermissionRepository` |
| Infrastructure | `SpacePermissionJpaEntity/-Repository/-Impl`, `SpaceGroupPermissionJpaEntity/-JpaRepository/-RepositoryImpl` (JPQL-join прав группы с членством) |
| Application | `SpaceService` (grant/revoke для пользователей и групп), `PermissionService` (canRead/canWrite/getUserPermissions) |
| REST | `SpaceController` (permissions + group-permissions), `PermissionController` (`GET /api/user/permissions`, `GET /api/user/spaces`) |

Ключевой фрагмент — проверка доступа GUEST с учётом групп (`PermissionService`):

```java
// GUEST: проверяем явные права (личные и групповые)
return permissionRepository.hasReadAccess(spaceId, userId)
        || groupPermissionRepository.hasReadAccessViaGroups(spaceId, userId);
```

JPQL «право через группу» (`SpaceGroupPermissionJpaRepository`):

```java
@Query("""
        SELECT COUNT(p) > 0 FROM SpaceGroupPermissionJpaEntity p, UserGroupMemberJpaEntity m
        WHERE p.groupId = m.groupId AND m.userId = :userId AND p.spaceId = :spaceId
        """)
boolean hasReadAccessViaGroups(@Param("spaceId") Long spaceId, @Param("userId") Long userId);
```

### API

| Метод | Эндпоинт | Ответы |
|-------|----------|--------|
| POST | `/api/admin/spaces/{spaceId}/permissions` `{userId, permissionType}` | 201; 404; 409 (дубликат/избыточное право) |
| GET | `/api/admin/spaces/{spaceId}/permissions` | 200 |
| DELETE | `/api/admin/permissions/{permId}` | 204 |
| POST | `/api/admin/spaces/{spaceId}/group-permissions` `{groupId, permissionType}` | 201; 404; 409 |
| GET | `/api/admin/spaces/{spaceId}/group-permissions` | 200 (с `groupName`) |
| DELETE | `/api/admin/group-permissions/{permId}` | 204 |
| GET | `/api/user/permissions?spaceId={id}` | 200: `{permissions, canRead, canEdit, canCreate}` |

### Ручное тестирование (сквозной сценарий с группой)

```bash
# Подготовка: создать пользователя GUEST (через /admin/users), пространство S, группу G,
# добавить GUEST в группу G (см. разделы выше). Залогиниться под GUEST во втором терминале.

# 1. GUEST до выдачи прав: пространств нет, доступа нет
curl http://localhost:8080/api/spaces --cookie "JWT=<guest>"          # []
curl "http://localhost:8080/api/user/permissions?spaceId=<S>" --cookie "JWT=<guest>"  # canRead:false

# 2. Админ выдаёт группе READ → 201
curl -X POST http://localhost:8080/api/admin/spaces/<S>/group-permissions --cookie "JWT=<admin>" \
  -H "Content-Type: application/json" -d '{"groupId":<G>,"permissionType":"READ"}'

# 3. GUEST теперь видит пространство и может читать (canRead:true, canEdit:false)

# 4. Админ выдаёт группе WRITE → GUEST может редактировать (canEdit:true)

# 5. Отзыв права/исключение из группы/удаление группы → доступ пропадает
curl -X DELETE http://localhost:8080/api/admin/group-permissions/<permId> --cookie "JWT=<admin>"
```

Аналогично для личных прав: `POST /api/admin/spaces/{id}/permissions` с `{userId, permissionType}`.
Через UI: `http://localhost:8080/admin/spaces` → Permissions.

### Тесты фичи

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test \
  -Dtest="PermissionIntegrationTest,GrantPermissionTest,GroupPermissionIntegrationTest"
```

| Тест | Проверяет |
|------|-----------|
| `PermissionIntegrationTest#myPermissions_guestFlags_dependOnSpacePermissions` | GUEST: нет прав → READ → WRITE, флаги canRead/canEdit/canCreate (сценарии 1–2 AC) |
| `PermissionIntegrationTest#myPermissions_adminAlwaysHasAllFlags_...` | админ имеет все права всегда; 404 для несуществующего пространства |
| `PermissionIntegrationTest#mySpaces_endpoint_returnsSpacesWithAnyPermission` | выданное право открывает пространство в списке |
| `GrantPermissionTest` | 201 для существующего пользователя, 404 для несуществующего |
| `GroupPermissionIntegrationTest` (4 теста) | наследование прав через группу, отзыв, исключение из группы, удаление группы, 404/409-сценарии (сценарии 1, 2, 4 AC) |

---

## 6. US4.3.1 — Рассылка Email-уведомлений

### Что делает

Асинхронная событийная рассылка почты. Сервисы публикуют доменные события, слушатели
`NotificationService` срабатывают **после коммита транзакции** (`AFTER_COMMIT`) — письмо
не уйдёт, если операция откатилась. Отправка выполняется в отдельном пуле потоков
(`mailTaskExecutor`, `@Async`) и не блокирует запрос; сбой SMTP не ломает бизнес-операцию.

События и письма:

| Событие | Кому | Письмо |
|---------|------|--------|
| `UserCreatedEvent` | новому пользователю | «Добро пожаловать в Базу знаний» |
| `SpacePermissionGrantedEvent` | пользователю | «Изменение прав доступа» (какое право, какое пространство) |
| `DocumentUpdatedEvent` | участникам пространства (кроме автора) | «Документ изменён: <название>» |

Конфигурация (см. `application.yml`, блок `app.notifications` + `spring.mail`):
`NOTIFICATIONS_ENABLED=false` (по умолчанию) — письма логируются (`LoggingEmailSender`),
SMTP не нужен; `true` — реальная отправка через `SpringMailEmailSender`/JavaMailSender.
SMTP-недоступность не влияет на `/actuator/health`.

### Код

| Слой | Файл |
|------|------|
| Domain | `domain/event/UserCreatedEvent.java`, `SpacePermissionGrantedEvent.java`, `DocumentUpdatedEvent.java`; порт `domain/notification/EmailSender.java`, `EmailMessage.java` |
| Application | `application/service/NotificationService.java` |
| Infrastructure | `infrastructure/notification/SpringMailEmailSender.java` (SMTP, @Async), `LoggingEmailSender.java` (заглушка); `infrastructure/config/NotificationConfig.java`, `NotificationProperties.java` |
| REST | `interfaces/rest/controller/NotificationAdminController.java` (`POST /api/admin/notifications/test`) |

Ключевой фрагмент — слушатель после коммита:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
public void onPermissionGranted(SpacePermissionGrantedEvent event) {
    User user = userRepository.findByIdIncludingDeleted(event.getUserId()).orElse(null);
    ...
    dispatch(user.getEmail(), "Изменение прав доступа в Базе знаний", body, "permission_granted");
}
```

### Ручное тестирование

```bash
# 1. Тестовое письмо (проверка SMTP-конфигурации), только админ → 202
curl -X POST http://localhost:8080/api/admin/notifications/test --cookie "JWT=<token>" \
  -H "Content-Type: application/json" -d '{"recipient":"you@example.com"}'
# → {"recipient":"you@example.com","queued":true,"notificationsEnabled":false}

# 2. При NOTIFICATIONS_ENABLED=false письмо видно в логах приложения:
docker logs kb_app --tail 50 | grep -i "email"

# 3. Событийные письма: создать пользователя через /api/admin/users (welcome-письмо),
#    выдать право через /api/admin/spaces/{id}/permissions (письмо о правах),
#    изменить документ (письма участникам пространства) — все видны в логах.

# 4. Реальный SMTP: в .env выставить NOTIFICATIONS_ENABLED=true, MAIL_HOST/PORT/USERNAME/PASSWORD,
#    перезапустить контейнер и повторить п.1 — письмо придёт на ящик.
```

### Тесты фичи

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test -Dtest=NotificationIntegrationTest
```

| Тест | Проверяет |
|------|-----------|
| `createUser_dispatchesWelcomeEmail` | welcome-письмо при создании пользователя |
| `grantPermission_dispatchesEmailToUser` | письмо о выданных правах (сценарий 1 AC) |
| `testEmail_asAdmin_returns202AndQueues` | тестовое письмо: 202 + постановка в очередь |
| `testEmail_withoutRecipient_usesAdminEmail` | fallback на `NOTIFICATIONS_ADMIN_EMAIL` |
| `testEmail_asNonAdmin_returns403` | запрет для не-админов |
| `testEmail_invalidRecipient_returns400` | валидация email |

В тестах реальный `EmailSender` подменяется записывающей заглушкой (`RecordingEmailSender`),
поэтому проверяется именно факт и содержимое сформированных писем без внешнего SMTP.

## 7. Итоговый прогон

```text
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
```

Разбивка по сьютам: AdminUser 8, Attachment 6, **AuditLog 4**, Auth 5, DocumentValidation 2,
**GrantPermission 2**, **Group 3**, **GroupPermission 4**, JGit 3, **Notification 6**,
**Permission 3**, RequirementNumber 1, Security 2, **Space 4**, Storage 1, ValidationError 3,
ApplicationContext 1. (Жирным — сьюты, относящиеся к шести US этого отчёта.)
