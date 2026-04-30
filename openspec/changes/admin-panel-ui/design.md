## Context

Админ-панель сейчас — набор заглушек:
- `admin-layout.html` — заготовка layout (sidebar с Users/Spaces, навбар, content placeholder). Стилей и полноценной структуры нет
- `admin-users.html`, `admin-spaces.html` — заглушки (4 строки, текст "список будет здесь")
- `admin-layout.html` подключает `/css/main.css` и `/js/main.js`, которые являются минимальными/заглушечными
- `AdminUserController` — полный REST CRUD пользователей + password reset
- `SpaceController` — частичный REST: GET list + POST create + grant permission
- Security: `SecurityConfig` защищает `/admin/**` и `/api/admin/**` через `hasRole("ADMIN")`

Страница `/admin/settings` не существует.

Прототип фронтендера: `../../Docs/documents/prototypes/admin-panel/index.html` — единый HTML-файл с тремя вкладками (Users, Spaces, Settings), inline CSS, клиентскими массивами данных. Стили прототипа — 100% кастомный CSS без фреймворков, Tailwind-подобная палитра. **Стили и визуальные компоненты берём исключительно из прототипа**, не из существующего `main.css` (который тоже заглушка).

## Goals / Non-Goals

**Goals:**
- Реализовать UI страницы Users с таблицей, поиском, фильтрами по ролям, пагинацией, модалками создания/редактирования/удаления/сброса пароля
- Реализовать UI страницы Spaces с таблицей, пагинацией, модалкой создания, интерфейсом назначения прав (grant permission)
- Кнопки Edit/Delete Space — заглушки ("В разработке"), т.к. backend эндпоинты отсутствуют
- Создать заглушку Settings ("В разработке")
- Общий JS-модуль с утилитами (CSRF, fetch, toast, modal)

**Non-Goals:**
- Update/Delete Space backend — пока нет эндпоинтов `PUT/DELETE /api/admin/spaces/{id}`
- Удаление Space Permissions — пока нет эндпоинта `DELETE /api/admin/spaces/{id}/permissions/{permissionId}`
- Системные настройки (theme, language) — заглушка, без backend API
- Корзина документов (trash) — не входит в текущий scope
- Аудит-логи — не входит в текущий scope
- Рефакторинг существующих domain/application слоёв — сервисы уже готовы

## Decisions

### 1. Архитектура страниц: отдельные Thymeleaf-фрагменты с inline JS

Каждая страница — отдельный Thymeleaf-фрагмент, встраиваемый в `admin-layout.html` через `<div th:replace="~{${content}}">`. JS-логика — inline `<script>` на каждой странице, с общим модулем `admin-common.js` для повторяющихся утилит.

**Альтернатива:** Одна SPA-страница с JS-табами.
**Отклонена** — проект использует SSR через Thymeleaf, отдельные страницы лучше вписываются в текущую архитектуру и упрощают дебаг.

### 1a. Одна роль в формах пользователей (vs массив ролей)

Фронтенд изначально реализован с чекбоксами ролей (Admin/Editor/Reader), что предполагало массив `roles: ["Admin", "Editor"]`. Однако бэкенд `CreateUserRequest` ожидает одиночное поле `role` типа `GlobalRole` (enum ADMIN/EDITOR/READER). Прототип не предусматривал multi-role.

**Решение:** Replace checkboxes с single `<select id="userRole">` содержащего значения enum. Фронтенд отправляет `{ login, email, role, password }` вместо `{ login, email, roles, status }`. Статус пользователя назначается бэкендом по умолчанию.

### 2. Данные: fetch API на клиенте + Server-Side Pagination

Страница при загрузке делает fetch-запрос к `/api/admin/users` и `/api/admin/spaces` для получения данных. Пагинация реализуется на сервере (backend уже поддерживает `page`, `size`, `sortBy`, `sortDir`). Клиент вызывает API при каждом изменении страницы/фильтра.

**Альтернатива:** Первичная загрузка через Thymeleaf model (`th:each`), затем fetch для CRUD. **Отклонена** — дублирование логики рендеринга (Thymeleaf vs JS DOM), сложнее поддерживать.

### 3. CSRF-защита: X-XSRF-TOKEN header

Frontend JS читает `XSRF-TOKEN` из cookie, отправляет как `X-XSRF-TOKEN` header во все POST/PUT/DELETE запросы. Это соответствует текущей конфигурации Spring Security (`CookieCsrfTokenRepository` + `.ignoringRequestMatchers("/api/**")`).

**Примечание:** Для `/api/**` CSRF игнорируется, но для `/admin/**` (SSR страницы, если будут формы) — активен. Fetch к REST API через `/api/admin/**` CSRF не требует, но для консистентности отправляем.

### 4. Стили: CSS из прототипа → отдельный файл `admin-panel.css`

Все существующие CSS/HTML файлы в проекте — заглушки без реальных стилей. Поэтому стили для админ-панели берём из прототипа `../../Docs/documents/prototypes/admin-panel/index.html` и выносим в `backend/src/main/resources/static/css/admin-panel.css`. Это включает: layout (sidebar + content + header), таблицы, кнопки (primary/secondary/danger), badges (active/inactive/Admin/Editor/Reader), модалки, toast notifications, фильтр-бар, пагинацию, sidebar tabs.

`admin-layout.html` и страницы подключают `admin-panel.css` вместо (или поверх) заглушечного `main.css`.

### 5. Модальные окна: vanilla JS, единый паттерн

Все модалки (user create/edit, space create/edit, delete confirm, password reset) — один тип: overlay `<div>` + centered card. Открытие/закрытие через `classList.toggle()`. Закрытие по клику на overlay, Escape key. Валидация форм — клиентская (HTML5 required + JS проверки).

### 6. Frontend Spaces page: частичная реализация по доступным backend эндпоинтам

Страница `/admin/spaces` использует доступные на бэкенде эндпоинты:
- `GET /api/admin/spaces` — загрузка списка пространств с пагинацией
- `POST /api/admin/spaces` — создание пространства
- `POST /api/admin/spaces/{spaceId}/permissions` — назначение права пользователю

Кнопки Edit/Delete Space показывают toast "Функция в разработке" — бэкенд эндпоинты `PUT/DELETE /api/admin/spaces/{id}` ещё не реализованы.

**Затронутые участки кода:**
- `admin-layout.html` — полная переработка: структура sidebar + nav из прототипа, добавление ссылки Settings
- `admin-users.html` — полная переработка: структура + inline JS из прототипа, адаптированный под fetch API к `/api/admin/users`
- `admin-spaces.html` — полная переработка: структура + inline JS, адаптированный под доступные эндпоинты `/api/admin/spaces`
- `admin-settings.html` — новый файл, заглушка
- `static/css/admin-panel.css` — новый файл, CSS из прототипа
- `static/js/admin-common.js` — новый файл, общие JS-утилиты (CSRF, fetch, toast, modal)

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **CSRF для API** — `/api/**` игнорируется в CSRF config, но если конфигурация изменится, admin JS сломается | Для `/api/admin/**` CSRF header опционален (ignored), отправляем только для консистентности |
| **Inline JS vs вынос в файл** — inline скрипты сложнее кэшировать | Общий модуль `admin-common.js` выносится в static, специфичная логика остаётся inline (уникальна для каждой страницы) |
| **Space delete с RESTRICT** — проверка на наличие документов может быть медленной при большом объёме данных | Использовать `EXISTS` запрос вместо `COUNT`, проверка только `documents` таблицы (FK constraint) |
| **Без поиска пользователей** — `AdminUserController` не поддерживает `search` параметр | Фронтенд фильтрация по загруженной странице (до page_size пользователей). Для полного поиска нужен backend update (отдельная задача) |
