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
- Base URL: `http://localhost:8080`

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

### 3) Максимально полный user функционал

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

### Полнота пользовательского UI (не заглушки)

- U11: главная страница должна показывать реальный список документов.
- U12: поиск должен показывать реальные результаты.
- U13: просмотр документа должен показывать контент документа.
- U14: история документа должна показывать историю версий.
- U15: страница пространства должна показывать дерево/список документов.

## Критерии приемки

- Минимум для “stage baseline”: зеленый `npm test`.
- Минимум для “жесткой приемки”: зеленый `npm run test:strict`.
- Минимум для “полной пользовательской готовности”: зеленый `npm run test:userfull`.

Если `strict` или `userfull` красные — это блокер для утверждения полноты пользовательского функционала.

## Последний полный прогон

- Команда: `npm test`
- Результат: **51 total / 38 passed / 13 failed**
- Детальный анализ причин: `E2E_FAILURE_ANALYSIS.md`

