# E2E

## Цель

Полный E2E-план для текущего продукта с разделением на:
- базовые проходные сценарии (реально работают сейчас),
- строгие приемочные сценарии (должны падать, если есть заглушки/WIP),
- максимально полное пользовательское покрытие.

## Окружение

- Backend: `mvn spring-boot:run` из `Project/backend`
- DB: локальный PostgreSQL (рабочий `kb_user` / `knowledge_base`)
- E2E workspace: `Project/tests/E2E`
- Base URL: `http://localhost:8081` (переопределение: `E2E_BASE_URL=http://localhost:8081 npm test`)
- Браузер: только **Chromium** (`npx playwright install chromium` локально; в Docker — то же через compose)

### Docker (profile `e2e`)

Поднять приложение, затем прогон тестов в одноразовом контейнере:

```bash
cd Project
docker compose --env-file .env up -d
make e2e-run
# или: docker compose --env-file .env --profile e2e run --rm e2e
```

Сервис `e2e` подключается к уже работающему `app` (`E2E_BASE_URL=http://app:8080`), ставит зависимости npm и **только Chromium** (`playwright install --with-deps chromium`).

## Наборы тестов

### 1) Baseline (текущее состояние этапа)

Запуск:

```bash
npm test
```

Что покрывает:
- auth smoke,
- admin users/spaces основные действия,
- доступность маршрутов,
- known limitations как ожидаемое поведение.

Ключевые файлы:
- `tests/auth.smoke.spec.js`
- `tests/admin-users.spec.js`
- `tests/admin-spaces.spec.js`
- `tests/content-placeholders.spec.js`
- `tests/assets-layout.spec.js`

### 2) Strict acceptance (жесткая приемка)

Запуск:

```bash
npm run test:strict
```

Что покрывает:
- без поблажек на заглушки и WIP,
- критичные ожидания по полноте пользовательских страниц и ассетов.

Ключевой файл:
- `tests/strict-acceptance.spec.js`

### 3) Documents (страницы и API документов)

Запуск:

```bash
npm run test:documents
```

Что покрывает:
- список документов: фильтры, поиск, удаление с подтверждением;
- создание, просмотр, редактирование (UI + Ctrl+S);
- API CRUD, валидация, `includeDeleted`, идемпотентное удаление;
- RBAC: READER/EDITOR, права на пространство;
- граничные случаи: XSS в markdown, unicode, длинный контент, несуществующие ID.

Ключевые файлы:
- `tests/documents.spec.js`
- `tests/helpers/documents.js`

### 4) Максимально полный user функционал

Запуск:

```bash
npm run test:userfull
```

Что покрывает:
- login/logout (UI + API),
- `/api/auth/me`,
- доступ к защищенным SSR-роутам,
- role-based ограничения (EDITOR/READER без доступа к ADMIN UI),
- `/api/user/spaces`, `/api/user/permissions`,
- проверка, что ключевые user-страницы не являются заглушками,
- расширенные negative/robustness кейсы (валидации, дубли, конфликтные и граничные параметры API).

Ключевой файл:
- `tests/user-full-functionality.spec.js`
- `tests/user-depth-negative.spec.js`
- `tests/coverage-gaps.spec.js`

## Сценарная матрица (full user)

### Auth / Session

- U1: форма логина содержит обязательные поля.
- U2: неверный логин через API отклоняется.
- U3: успешный логин через API + корректный `/api/auth/me`.
- U4: анонимный запрос к защищенному user API отклоняется.
- U6: logout через UI инвалидирует сессию.

### Навигация / Доступность страниц

- U5: авторизованный пользователь открывает все основные SSR-маршруты:
  - `/`
  - `/documents/new`
  - `/documents/{id}`
  - `/documents/{id}/edit`
  - `/documents/{id}/history`
  - `/search?q=...`
  - `/spaces/{id}`

### RBAC (ролевая модель)

- U7: пользователь с ролью EDITOR не должен иметь доступ к admin-панелям.
- U8: пользователь с ролью READER не должен иметь доступ к admin-панелям.

### User API

- U9: `/api/user/spaces` возвращает корректный JSON для авторизованного пользователя.
- U10: `/api/user/permissions?spaceId=...` возвращает флаги `canRead/canEdit/canCreate`.

### RBAC (новое ядро)

- U3: `/api/auth/me` возвращает `role` (`GUEST`/`READER`/`EDITOR`) и `isAdmin` (не роль `ADMIN`).
- U16: создание пользователя с ролью `GUEST` через API.
- U17: `EDITOR` без `isAdmin` не имеет доступа к admin API.

### Полнота пользовательского UI

- U11: главная — реальный список документов (не заглушка).
- U12–U15: маршруты открываются; поиск, история и пространство пока с WIP-заглушками (см. D2).
- U13: просмотр документа — динамическая загрузка контента.

## Критерии приемки

- Минимум для “stage baseline”: зеленый `npm test`.
- Минимум для “жесткой приемки”: зеленый `npm run test:strict`.
- Минимум для “полной пользовательской готовности”: зеленый `npm run test:userfull`.

Если `strict` или `userfull` красные — это блокер для утверждения полноты пользовательского функционала.

## Последний полный прогон

- Команда: `npm test`
- Результат: **57 total / 53 passed / 4 skipped** (`test.fixme` для ещё не реализованных strict-сценариев)
- WIP-страницы (поиск, история, пространство, admin settings) отмечены в `content-placeholders.spec.js` (D2) и strict `@fixme`

