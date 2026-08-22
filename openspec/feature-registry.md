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
- **status**: done
- **dependsOn**: `spaces-and-permissions`
- **endpoints**: `GET/POST/PUT/DELETE /api/admin/spaces`, `POST /api/admin/spaces/{id}/restore`
- **docs**: `../../Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.2 Управление Пространствами/US4.2.1 CRUD сущности Space/description.md`

### add-email-notifications — Email notifications: async event-driven email + admin SMTP test
- **status**: done
- **dependsOn**: `admin-users-crud`, `spaces-and-permissions`, `documents-crud`
- **endpoints**: `POST /api/admin/notifications/test`
- **docs**: `../../Docs/documents/backlog-descriptions/E4 Администрирование и Безопасность (Admin & Security)/F4.3 Системные уведомления/US4.3.1 Рассылка Email-уведомлений/description.md`, `.../US4.3.2 Настройка параметров SMTP и асинхронной рассылки/description.md`

### audit-log — Audit log: system action logging + admin audit endpoint (US4.1.5)
- **status**: done
- **dependsOn**: `admin-users-crud`, `space-crud`, `documents-crud`
- **endpoints**: `GET /api/admin/audit`
- **docs**: `.../F4.1 Управление пользователями (RBAC)/US4.1.5 Логирование действий системы/description.md`

### admin-groups-ui — Admin Panel: группы, членство и права групп в интерфейсе (US4.1.8 / US4.1.9 / US4.2.2)
- **status**: done
- **dependsOn**: `user-groups`, `admin-panel-ui`
- **endpoints**: `GET /admin/groups` (новых REST-эндпоинтов нет — UI использует существующий API)
- **docs**: `.../US4.1.8 Создание и управление группами/description.md`, `.../US4.1.9 Управление членством в группах/description.md`

### ai-text-assistant — Нейроассистент редактирования текста (routerai)
- **status**: done
- **dependsOn**: `documents-crud`
- **endpoints**: `GET /api/ai/status`, `POST /api/ai/transform`
- **config**: `AI_ENABLED`, `AI_API_KEY`, `AI_MODEL` (по умолчанию `deepseek/deepseek-v4-pro`), `AI_BASE_URL`, `AI_TIMEOUT_SECONDS`

### user-groups — User groups: CRUD, membership and group space permissions (US4.1.8 / US4.1.9 / US4.2.2)
- **status**: done
- **dependsOn**: `admin-users-crud`, `spaces-and-permissions`
- **endpoints**: `GET/POST /api/admin/groups`, `GET/PUT/DELETE /api/admin/groups/{id}`, `GET/POST /api/admin/groups/{id}/members`, `DELETE /api/admin/groups/{id}/members/{userId}`, `POST/GET /api/admin/spaces/{spaceId}/group-permissions`, `DELETE /api/admin/group-permissions/{permId}`
- **docs**: `.../F4.1 Управление пользователями (RBAC)/US4.1.8 Создание и управление группами/description.md`, `.../US4.1.9 Управление членством в группах/description.md`
