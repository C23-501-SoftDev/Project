## Why

Административная панель в настоящее время имеет только каркас: `admin-users.html` и `admin-spaces.html` содержат заглушки без функциональности. Backend API для управления пользователями и пространствами уже реализован и готов к использованию. Необходимо связать существующие REST-эндпоинты с полноценным UI, чтобы администраторы могли управлять пользователями и пространствами через браузер.

## What Changes

- **UI: Users page** — полноценная страница `/admin/users` с таблицей пользователей, поиском, фильтрацией по ролям, пагинацией, модальными формами создания/редактирования, подтверждением удаления, сбросом пароля
- **UI: Spaces page** — страница `/admin/spaces` с таблицей пространств, пагинацией, модалкой создания пространства, интерфейсом назначения прав (grant permission). Кнопки Edit/Delete — заглушки, т.к. backend эндпоинты `PUT/DELETE /api/admin/spaces/{id}` ещё не реализованы
- **UI: Settings stub** — заглушка `/admin/settings` с сообщением "В разработке"
- **Common JS utilities** — модуль `admin-common.js` с утилитами: CSRF token getter, fetch wrapper, toast notifications, modal helpers

## Capabilities

### New Capabilities
- `admin-panel-ui`: Полноценный UI административной панели для управления пользователями (CRUD), управления пространствами (view, create, grant permission), заглушка для настроек. Доступ разрешён только пользователям с ролью ADMIN через существующую защиту `/admin/**` (SecurityConfig) и `@PreAuthorize("hasRole('ADMIN')")`.

### Modified Capabilities
- *(none — требования к существующим spec-файлам не меняются, добавляются новые эндпоинты и UI)*

## Impact

**Изменяемые файлы:**
- `backend/src/main/resources/templates/pages/admin-users.html` — полная замена заглушки на UI из прототипа, адаптированный под fetch API
- `backend/src/main/resources/templates/pages/admin-spaces.html` — полная замена заглушки на UI из прототипа, адаптированный под доступные fetch API (`GET`, `POST /api/admin/spaces`)
- `backend/src/main/resources/templates/pages/admin-settings.html` — новый файл, заглушка
- `backend/src/main/resources/templates/admin-layout.html` — полная замена заглушки на layout из прототипа, добавление ссылки "Настройки"
- `backend/src/main/resources/static/css/admin-panel.css` — новый файл, CSS-стили из прототипа
- `backend/src/main/resources/static/js/admin-common.js` — новый файл с общими JS-утилитами

Примечание: все существующие HTML/CSS файлы в проекте — заглушки. Стили, структура layout, модалок, таблиц, кнопок, badges полностью берутся из прототипа `../../Docs/documents/prototypes/admin-panel/index.html`.

**Feature Registry:**
- `openspec/feature-registry.json` — запись `admin-panel-ui` со статусом `planned`
