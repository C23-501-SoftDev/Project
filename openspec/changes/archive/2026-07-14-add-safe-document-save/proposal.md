# Proposal: add-safe-document-save

## Why

Текущая реализация сохранения документа в `document-edit.html` использует вспомогательную функцию `apiFetch`, которая уже умеет читать CSRF-токен из Cookie `XSRF-TOKEN` и отправлять его в заголовке `X-XSRF-TOKEN`. Однако обработка HTTP-ответов при сохранении непоследовательна:

- **401 Unauthorized** не перенаправляет на `/login` — пользователь «застревает» с непонятной ошибкой.
- **403 Forbidden** (истёкший или украденный CSRF-токен) не сообщает пользователю о необходимости обновить страницу для получения нового токена.
- **400 Bad Request** (ошибки валидации) отображается единым сообщением, без разбивки по полям.
- **Автосохранение черновика** отсутствует — пользователь рискует потерять несохранённые изменения при закрытии вкладки или сетевом сбое.

Без этих улучшений любая ротация CSRF-токена или истечение JWT-сессии превращается в немое «Ошибка сохранения», вынуждая пользователя самостоятельно разбираться в причине.

## What Changes

- Рефакторинг функции `saveDocument()` в `document-edit.html` с явной обработкой статусов 200 / 400 / 401 / 403.
- Добавление `autosave`-механизма на основе `debounce` (`setTimeout` 3 с) для textarea/title.
- Утилита `getCsrfToken()` уже присутствует в `main.js` и используется через `apiFetch`; расширение не требуется.
- Индикатор состояния автосохранения («Сохранено», «Несохранённые изменения», «Сохранение...»).

## Capabilities

### Modified Capabilities
- `documents-crud` → PUT `/api/documents/{id}` — клиентская сторона получает корректную обработку всех ответов API.

### New Capabilities
- `safe-document-save`: Механизм безопасного сохранения с CSRF-защитой, обработкой ошибок по статусам и debounce-автосохранением.

## Impact

- **Frontend (Thymeleaf template)**: `document-edit.html` — замена функции `saveDocument()`, добавление `setupAutosave()`.
- **Static JS** (`main.js`): `getCsrfToken()` и `apiFetch()` не изменяются — они уже реализуют нужное поведение.
- **Backend**: Изменений не требуется; PUT `/api/documents/{id}` уже реализован и возвращает корректные статусы.
- **Security**: Явная обработка 401/403 устраняет риск молчаливого игнорирования ошибок безопасности.

## References

- Существующая реализация: `backend/src/main/resources/templates/pages/document-edit.html`
- Базовые утилиты: `backend/src/main/resources/static/js/main.js` — `getCsrfToken()`, `apiFetch()`, `showToast()`
- Spring Security CSRF: Cookie `XSRF-TOKEN` → заголовок `X-XSRF-TOKEN`
- JWT: HttpOnly Cookie `JWT` (браузер отправляет автоматически при `credentials: 'same-origin'`)
