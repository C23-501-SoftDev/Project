# Feature registry (быстрый выбор фичи)

Этот файл дублирует `openspec/feature-registry.json`, но удобнее для чтения человеком.

## Как использовать в общении с агентом
- Агент **сначала читает** `openspec/feature-registry.json`
- Затем спрашивает: **какую фичу реализовать** (по `featureId`)
- После завершения изменений агент **обновляет статус** фичи и/или добавляет новую запись

## Фичи

### auth-jwt-cookie — JWT authentication (login + me)
- **status**: done
- **endpoints**: `POST /api/auth/login`, `GET /api/auth/me`

### admin-users-crud — Admin: users CRUD + change password
- **status**: done
- **endpoints**: `GET/POST/PUT/DELETE /api/admin/users`, `PUT /api/admin/users/{id}/password`

### spaces-and-permissions — Spaces + permissions
- **status**: done
- **endpoints**: `GET /api/spaces`, `GET/POST /api/admin/spaces`, `POST /api/admin/spaces/{spaceId}/permissions`, `GET /api/user/*`

### admin-panel-ui — Admin Panel: Users & Spaces management UI
- **status**: done
- **dependsOn**: `admin-users-crud`, `spaces-and-permissions`
- **endpoints**: `GET /admin/users`, `GET /admin/spaces`, `GET /admin/settings`, `GET/POST/PUT/DELETE /api/admin/users`, `GET/POST/PUT/DELETE /api/admin/spaces`
- **docs**: `../Docs/documents/prototypes/admin-panel/index.html`

### global-refactor-role-model-and-user — Global refactor: Role model and user
- **status**: done
- **dependsOn**: `auth-jwt-cookie`, `admin-users-crud`, `spaces-and-permissions`

### space-crud — Spaces: Full CRUD operations for Admin
- **status**: pending
- **dependsOn**: `spaces-and-permissions`
- **endpoints**: `GET/POST/PUT/DELETE /api/admin/spaces`
- **docs**: `../../Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.2 Управление Пространствами/US4.2.1 CRUD сущности Space/description.md`

### user-group-management — Admin: user groups CRUD
- **status**: pending
- **dependsOn**: `admin-users-crud`
- **endpoints**: `GET/POST/PUT/DELETE /api/admin/groups`, `GET /api/admin/groups/{groupId}`
- **docs**: `../../Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.1 Управление пользователями (RBAC)/US4.1.8 Создание и управление группами/description.md`

### user-group-membership — Admin: user group membership management
- **status**: pending
- **dependsOn**: `user-group-management`
- **endpoints**: `GET/POST /api/admin/groups/{groupId}/members`, `DELETE /api/admin/groups/{groupId}/members/{userId}`
- **docs**: `../../Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.1 Управление пользователями (RBAC)/US4.1.9 Управление членством в группах/description.md`
