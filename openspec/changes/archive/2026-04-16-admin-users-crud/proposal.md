## Why

Администраторы приложения Knowledge Base нуждаются в полном жизненном цикле управления пользователями: создание, просмотр, обновление, удаление и сброс паролей. Эта функциональность обеспечивает административный контроль над системой, позволяя администраторам назначать роли, обновлять учётные данные и поддерживать целостность данных при удалении.

Implements feature `admin-users-crud` from `openspec/feature-registry.json`.
Based on docs: `D:/Soft-dev/Project/backend/readme.md`

## What Changes

- **NEW**: `AdminUserController` — REST CRUD controller at `/api/admin/users` with @PreAuthorize("hasRole('ADMIN')")
- **NEW**: `UserService.createUser()` — создание пользователя с проверкой уникальности login/email, BCrypt-хешированием пароля, публикацией `UserCreatedEvent`
- **NEW**: `UserService.updateUser()` — обновление login, email, роли; проверки уникальности при изменении
- **NEW**: `UserService.changePassword()` — сброс пароля через отдельный PUT эндпоинт
- **NEW**: `UserService.deleteUser()` — удаление с ON DELETE RESTRICT проверками (документы, пространства, версии)
- **NEW**: `UserService.getAllUsers()` — список всех пользователей с пагинацией и сортировкой
- **NEW**: DTOs: `CreateUserRequest`, `UpdateUserRequest`, `ChangePasswordRequest`
- **NEW**: Endpoint `PUT /api/admin/users/{id}/password` — сброс пароля
- **NEW**: Двойная защита: SecurityConfig route rules (@see `/api/admin/**`) + @PreAuthorize на уровне контроллера

## Capabilities

### New Capabilities
- `admin-user-management`: Полный CRUD жизненный цикл управления пользователями администратором. Включает создание с BCrypt-хешированием, обновление профиля и роли, удаление с проверкой ссылочной целостности, сброс паролей и пагинацию списка.

### Modified Capabilities
- *(none — new standalone capability)*

## Impact

- **Backend layers**: `application/service/UserService`, `interfaces/rest/controller/AdminUserController`, `interfaces/rest/dto/request/*`, `infrastructure/security/config/*` (existing — no modifications needed)
- **Existing dependencies**: Requires `auth-jwt-cookie` change to be in place (admin-only endpoints already configured via SecurityConfig)
- **External interfaces**: REST API only — `GET/POST/PUT/DELETE /api/admin/users`, `PUT /api/admin/users/{id}/password`
- **Database**: Existing `users` table; ON DELETE RESTRICT на `documents.author_id`, `spaces.owner_id`, `versions.author_id`
- **Events**: `UserCreatedEvent` публикуется при создании пользователя
- **Breaking**: *(none)*
