## Why

Knowledge Base необходимо организовать документы в логические пространства (spaces) с двухуровневым RBAC: глобальная роль пользователя (ADMIN, EDITOR, READER) определяет базовый уровень доступа, а права на уровне пространства (READ, WRITE, OWNER) уточняют, к каким именно документам пользователь имеет доступ. Без пространств все документы были бы плоским списком без контроля видимости и без возможности делегирования прав владения.

Implements feature `spaces-and-permissions` from `openspec/feature-registry.json`.
Based on docs: `D:/Soft-dev/Project/backend/readme.md`

## What Changes

- **NEW**: Domain models `Space` (пространство документов) and `SpacePermission` (право доступа к пространству)
- **NEW**: Domain enum `PermissionType` (READ, WRITE, OWNER) with hierarchical permissions
- **NEW**: `SpaceService` — создание пространства (авто-IOWNER), список всех, список по пользователю, назначение прав
- **NEW**: `PermissionService` — проверка прав canRead/canWrite, получение флагов для UI (canRead, canEdit, canCreate)
- **NEW**: `SpaceController` — ADMIN endpoints: `GET /api/admin/spaces`, `POST /api/admin/spaces`, `POST /api/admin/spaces/{spaceId}/permissions`; User endpoint: `GET /api/spaces`
- **NEW**: `PermissionController` — user endpoints: `GET /api/user/permissions?spaceId={id}`, `GET /api/user/spaces`
- **NEW**: DTOs: `CreateSpaceRequest`, `GrantPermissionRequest`, `SpaceResponse`, `SpacePermissionResponse`, `UserPermissionsResponse`
- **NEW**: Repositories: `SpaceRepository`, `SpacePermissionRepository` with queries for permissions by userId/spaceId
- **NEW**: RBAC logic — ADMIN bypasses all space permissions, EDITOR needs WRITE/OWNER, READER needs any permission

## Capabilities

### New Capabilities
- `space-management`: Создание и перечисление пространств документов. Включает создание с AUTO-GRANT OWNER, просмотр всех пространств (ADMIN), просмотр своих пространств (по роли), уникальность имен пространств.
- `space-permissions`: Назначение и проверка прав доступа к пространствам. Включает назначение прав (READ/WRITE/OWNER), проверку canRead/canWrite по двухуровневому RBAC, получение UI-флагов (canEdit, canCreate).

### Modified Capabilities
- *(none — new standalone capability built atop `auth-jwt-cookie` and `admin-user-management`)*

## Impact

- **Backend layers**: `domain/model/*` (Space, SpacePermission, PermissionType), `domain/repository/*` (SpaceRepository, SpacePermissionRepository), `application/service/*` (SpaceService, PermissionService), `interfaces/rest/controller/*` (SpaceController, PermissionController), `interfaces/rest/dto/*`
- **Existing dependencies**: Requires `auth-jwt-cookie` (JWT authentication for user context) and `admin-user-management` (admin-only endpoints)
- **External interfaces**:
  - ADMIN REST: `GET /api/admin/spaces`, `POST /api/admin/spaces`, `POST /api/admin/spaces/{spaceId}/permissions`
  - User REST: `GET /api/spaces`, `GET /api/user/permissions`, `GET /api/user/spaces`
- **Database**: New tables `spaces` and `space_permissions` with foreign keys to `users`
- **Breaking**: *(none)*
