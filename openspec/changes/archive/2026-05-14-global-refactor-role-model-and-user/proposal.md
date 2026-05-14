## Why

Текущая модель ролей использует плоский enum `ADMIN/EDITOR/READER`, где ADMIN — отдельная роль с полным доступом. Актуальная документация (`070.role-matrix.md`, `120.data-model.md`, `050.entities.md`) определяет двухкомпонентную модель: глобальная роль (`GUEST/READER/EDITOR`) + независимый флаг `is_admin`. Дополнительно, пользователи должны поддерживать soft-удаление через флаг `is_deleted` вместо физического удаления — это сохраняет историю авторства документов и аудита. Текущий код не соответствует этим требованиям.

Документы:
- `Docs/documents/070.role-matrix.md`
- `Docs/documents/120.data-model.md`
- `Docs/documents/050.entities.md`
- `Docs/documents/030.actors.md`
- `Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.1 Управление пользователями (RBAC)/US4.1.2 Создание сущности User и JWT авторизация/description.md`
- `Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.1 Управление пользователями (RBAC)/US4.1.3 Реализация RBAC/description.md`
- `Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.1 Управление пользователями (RBAC)/US4.1.7 Soft-удаление и восстановление пользователей/description.md`

FeatureId: `global-refactor-role-model-and-user`

## What Changes

- **Role model refactor**: Enum `GlobalRole` изменяется с `ADMIN/EDITOR/READER` на `GUEST/READER/EDITOR`
- **New `is_admin` field**: В сущность User добавляется поле `is_admin` (boolean, default false), отдельное от глобальной роли
- **Soft-delete вместо hard-delete**: В сущность User добавляется поле `is_deleted` (boolean, default false). Эндпоинт `DELETE /api/admin/users/{id}` больше не физически удаляет, а ставит флаг `is_deleted = true`
- **New restore endpoint**: Добавляется `POST /api/admin/users/{id}/restore` для восстановления soft-удалённых пользователей
- **Auth blocking for deleted users**: Аутентификация блокируется для пользователей с `is_deleted = true`
- **JWT claims update**: JWT-токен теперь содержит claims `role` (GUEST/READER/EDITOR) и `is_admin` (boolean)
- **User list filtering**: Список пользователей по умолчанию возвращает только активных (`is_deleted = false`), с опциональным включением удалённых
- **Admin UI update**: UI админ-панели отображает `is_admin` флаг отдельно от роли, статус soft-delete, кнопку восстановления вместо удаления
- **Security config update**: Защита `/admin/**` и `/api/admin/**` переключается с `hasRole("ADMIN")` на проверку `is_admin = true`

## Capabilities

### New Capabilities
- `user-soft-delete`: Soft-удаление и восстановление пользователей. Эндпоинты DELETE (soft) и POST restore. Блокировка аутентификации для удалённых. Флаг `is_deleted` в JWT не включается, но проверяется при login.
- `user-restore`: Восстановление soft-удалённого пользователя администратором. Эндпоинт `POST /api/admin/users/{id}/restore`.

### Modified Capabilities
- `jwt-cookie-authentication`: JWT claims меняются — `role` теперь принимает значения GUEST/READER/EDITOR (вместо ADMIN/EDITOR/READER). Добавляется claim `is_admin` (boolean).
- `admin-user-management`: CRUD пользователей меняется — CREATE/UPDATE поддерживают `is_admin` флаг. DELETE становится soft-delete. Добавляется restore. Список пользователей фильтрует удалённых по умолчанию. Роли в UI: GUEST/READER/EDITOR + чекбокс isAdmin.

## Impact

**Backend:**
- `domain/model/GlobalRole.java` — enum значения: ADMIN→GUEST, dbValue "Admin"→"Guest"
- `domain/model/User.java` — добавление поля `isAdmin` и `isDeleted`, методы `softDelete()`, `restore()`, `isDeleted()`
- `domain/repository/UserRepository.java` — новые методы: `findByLoginAndIsDeletedFalse`, `findAllActive`, `restoreById`
- `infrastructure/persistence/entity/UserJpaEntity.java` — колонки `is_admin`, `is_deleted`
- `infrastructure/persistence/mapper/UserJpaMapper.java` — маппинг новых полей
- `infrastructure/persistence/repository/UserJpaRepository.java` — JPA queries для soft-delete
- `infrastructure/persistence/repository/UserRepositoryImpl.java` — реализация новых методов
- `infrastructure/security/jwt/JwtTokenProvider.java` — добавление claim `is_admin`
- `infrastructure/security/config/SecurityConfig.java` — замена `hasRole("ADMIN")` на кастомную проверку `is_admin`
- `application/service/AuthService.java` — проверка `is_deleted` при аутентификации
- `application/service/UserService.java` — soft-delete вместо hard-delete, restore, фильтрация по `is_deleted`
- `interfaces/rest/controller/AdminUserController.java` — изменение DELETE на soft-delete, добавление POST restore, обновление DTO
- `interfaces/rest/controller/AuthController.java` — проверка `is_deleted` при login
- `interfaces/rest/dto/request/CreateUserRequest.java` — добавление `isAdmin` field
- `interfaces/rest/dto/request/UpdateUserRequest.java` — добавление `isAdmin` field
- `interfaces/rest/dto/response/UserResponse.java` — добавление `isAdmin`, `isDeleted` fields
- `interfaces/rest/mapper/RestDtoMapper.java` — маппинг новых полей
- `resources/db/changelog/` — колонки `is_admin`, `is_deleted`, CHECK constraint; миграция написана отдельно (БД пересобирается)

**Frontend (Thymeleaf + JS):**
- `templates/pages/admin-users.html` — обновление UI: отображение `is_admin` отдельно от роли, статус Active/Deleted, кнопка Restore вместо Delete, фильтр по статусу
- `templates/layout.html` — обновление проверки isAdmin в навигации

**Specs:**
- `openspec/specs/jwt-cookie-authentication/spec.md` — обновление claims
- `openspec/specs/admin-user-management/spec.md` — обновление CRUD, soft-delete, restore
