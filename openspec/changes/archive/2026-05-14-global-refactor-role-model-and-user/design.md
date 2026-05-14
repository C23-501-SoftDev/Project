## Context

Текущий код использует плоский enum `GlobalRole` со значениями `ADMIN`, `EDITOR`, `READER`. Роль `ADMIN` даёт одновременно и доступ к админ-панели, и полный доступ к контенту. По актуальной документации модель должна быть двухкомпонентной:

1. **Глобальная роль** (`GUEST` / `READER` / `EDITOR`) — определяет права на контент
2. **Флаг `is_admin`** — независимый boolean, определяющий доступ к панели администратора

Также текущий код выполняет hard-delete пользователей, что ломает историю авторства документов. Документация требует soft-delete через флаг `is_deleted` с возможностью восстановления.

## Goals / Non-Goals

**Goals:**
- Рефактор `GlobalRole` enum: убрать `ADMIN`, добавить `GUEST`
- Добавить поле `is_admin` в User доменную модель, JPA entity, DTO, JWT claims
- Добавить поле `is_deleted` в User доменную модель, JPA entity, DTO
- Заменить hard-delete на soft-delete + добавить restore endpoint
- Обновить AuthService для блокировки аутентификации soft-удалённых пользователей
- Обновить SecurityConfig для проверки `is_admin` вместо `hasRole("ADMIN")`
- Обновить админ-панель UI: отдельное отображение роли и флага админа, статус deleted/active, restore

**Non-Goals:**
- Не добавляем группы пользователей (UserGroup) — это отдельная фича
- Не добавляем SpaceUserPermission/SpaceGroupPermission — это отдельная фича
- Не меняем структуру PermissionService / RBAC на уровне пространств — только глобальные изменения
- Не удаляем существующие эндпоинты — только модифицируем поведение

## Decisions

### Decision 1: GlobalRole enum — замена ADMIN на GUEST

**Решение:** Enum `GlobalRole` будет содержать `GUEST`, `READER`, `EDITOR`. Значения в БД: "Guest", "Reader", "Editor".

**Альтернативы:**
- Добавить GUEST, оставить ADMIN — не соответствует документации, где ADMIN это флаг, а не роль
- Создать отдельный enum для ролей контента — избыточно, т.к. уже есть GlobalRole

**Рациональ:** Документация однозначно определяет 3 глобальные роли + флаг. БД пересобирается с нуля, перенос данных не требуется.

### Decision 2: `is_admin` — отдельное поле в User

**Решение:** В доменной модели `User` поле `isAdmin` (boolean, default false). В JWT добавляется claim `is_admin`. В SecurityConfig используем кастомный `AuthorizationManager` вместо `hasRole("ADMIN")`.

**Альтернативы:**
- `Authority` based — добавлять ROLE_ADMIN в authorities. Не работает, т.к. Spring Security authorities выводятся из enum, а enum теперь не содержит ADMIN
- Отдельный AdminUser entity — избыточная сложность, два флаг-поля достаточно

### Decision 3: Soft-delete через флаг `is_deleted`, а не отдельную таблицу

**Решение:** Поле `is_deleted` (boolean, default false) в таблице `users`. Все репозиторий-методы по умолчанию фильтруют `is_deleted = false`. Отдельные методы `findByIdIncludingDeleted`, `findAllIncludingDeleted` для админ-панели.

**Альтернативы:**
- Отдельная таблица `deleted_users` — сложнее, требует дублирования данных
- PostgreSQL row-level security — избыточно для одного флага
- Soft-delete через `deleted_at` timestamp — можно добавить позже, boolean проще для начала

**Рациональ:** Boolean — минимальное изменение. `login` и `email` остаются уникальными всегда (даже для удалённых), что предотвращает повторную регистрацию.

### Decision 4: DELETE endpoint → soft-delete, restore endpoint

**Решение:** `DELETE /api/admin/users/{id}` меняет поведение с hard-delete на soft-delete. Проверки referential integrity больше не блокируют удаление (т.к. данные физически остаются). Новый `POST /api/admin/users/{id}/restore` сбрасывает `is_deleted = false`.

**Альтернативы:**
- Оставить DELETE как hard-delete + добавить PATCH для soft-delete — нарушает ожидания, что DELETE безопасен
- Отдельный `/api/admin/users/{id}/deactivate` — более понятное имя, но proposal уже определил soft-delete как DELETE

### Decision 5: SecurityConfig — кастомная проверка `is_admin`

**Решение:** Вместо `.hasRole("ADMIN")` используем `.access(new IsAdminAuthorizationManager())`. Менеджер проверяет `Authentication.getPrincipal()` — если это `User`, то `user.isAdmin()`.

**Альтернативы:**
- Оставить ROLE_ADMIN в Spring Security authorities — требует изменения JwtCookieAuthenticationFilter для добавления ROLE_ADMIN когда `is_admin=true`. Это проще в реализации.

**Выбранная альтернатива:** Добавляем `ROLE_ADMIN` в authorities в `JwtCookieAuthenticationFilter` когда `user.isAdmin() = true`. Это минимальное изменение — не требует нового AuthorizationManager, совместимо с существующим `.hasRole("ADMIN")` в SecurityConfig и `@PreAuthorize("hasRole('ADMIN')")` в контроллерах.

### Decision 6: JWT TokenProvider — добавление claim `is_admin`

**Решение:** В `generateToken()` добавляем `.claim("isAdmin", user.isAdmin())`. Валидация токена не меняется — claim просто читается при необходимости.

### Decision 7: Domain model — методы `softDelete()` и `restore()`

**Решение:** В `User` добавляем методы домена:
- `void softDelete()` — устанавливает `isDeleted = true`
- `void restore()` — устанавливает `isDeleted = false`
- `boolean isDeleted()` — getter
- `boolean isActive()` — возвращает `!isDeleted`

`isAdmin()` переопределяется: возвращает `isAdmin` field вместо проверки роли.

### Decision 8: Миграция БД

**Решение:** Колонки `is_admin`, `is_deleted` и CHECK constraint будут описаны в Liquibase-миграции, которая пишется отдельно. Перенос данных не требуется — БД пересобирается с нуля. Миграция должна содержать:
1. ALTER TABLE ADD COLUMN `is_admin` BOOLEAN NOT NULL DEFAULT FALSE
2. ALTER TABLE ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE
3. ALTER TABLE MODIFY CHECK constraint: role IN ('Guest', 'Reader', 'Editor')
4. Добавление частичного индекса `idx_users_not_deleted` на `(id) WHERE is_deleted = false`

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **`GlobalRole.fromDbValue()` сломается на старых JWT (legacy "Admin")** | Добавляем fallback: "Admin" → EDITOR для обратной совместимости при чтении JWT. |
| **Soft-delete не проверяет referential integrity** | Т.к. данные физически не удаляются, проверки не нужны. Но soft-удалённый пользователь остаётся в БД и его login/email зарезервированы. Это intentional по дизайну. |
| **UI admin-панели может показывать удалённых пользователей в обычном списке** | По умолчанию API возвращает только active. Для показа удалённых — отдельный query parameter `?includeDeleted=true`. |

## Migration Plan

БД пересобирается с нуля. Liquibase-миграция для колонок `is_admin`, `is_deleted` и CHECK constraint пишется отдельно. Перенос данных не требуется.

Порядок имплементации:
1. **Добавить fallback** в `GlobalRole.fromDbValue()` для "Admin" → EDITOR
2. **Добавить поля** в доменную модель и JPA entity
3. **Обновить JWT TokenProvider** — добавить claim `isAdmin`
4. **Обновить JwtCookieAuthenticationFilter** — добавлять ROLE_ADMIN authority если `isAdmin=true`
5. **Обновить UserService** — soft-delete вместо hard-delete, restore, фильтрация
6. **Обновить AuthService** — блокировка аутентификации удалённых
7. **Обновить DTO** — добавить `isAdmin`, `isDeleted`
8. **Обновить AdminUserController** — новый restore endpoint
9. **Обновить UI** — админ-панель
10. **Написать Liquibase-миграцию** для структуры (без переноса данных)


