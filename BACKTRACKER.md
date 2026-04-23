# BACKTRACKER — Страницы и API-запросы

> **Цель:** Документ для синхронизации фронтенд- и бэкенд-разработки. Для каждой страницы указаны все API-запросы, которые она использует или должна использовать.
>
> **Легенда статусов:**
> - ✅ — API реализован
> - ❌ — API НЕ реализован (требуется разработка)
> - 🔶 — Частично реализован

> ⚠️ **Правило поддержки:**
> Если разработчик добавляет, изменяет или удаляет API-эндпоинты, то он обязан обновить этот файл: изменить статусы (❌→✅), добавить/убрать строки, обновить «Дату обновления» в шапке.

**Дата обновления:** 2026-04-23

---

## Аутентификация

### Страница входа (`GET /login`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| POST | `/login` | HTML-форма входа → установка JWT Cookie + CSRF Cookie, редирект на `/` | ✅ |
| POST | `/api/auth/login` | REST-вход (JSON) → `{ token, user }` + HttpOnly Cookie | ✅ |

**После входа:** редирект на `/` (главная страница)

---

### Выход (`POST /logout`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| POST | `/logout` | Очистка JWT Cookie → редирект на `/login` | ✅ |

---

## Главная страница — Список документов (`GET /`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/spaces` | Список пространств, доступных текущему пользователю | ✅ |
| GET | `/api/user/permissions?spaceId={id}` | Права текущего пользователя в пространстве (canRead, canEdit, canCreate) | ✅ |
| GET | `/api/documents?page=0&size=20&sortBy=title&sortDir=asc` | Список документов с пагинацией | ❌ |
| GET | `/api/documents?spaceId={id}` | Фильтрация документов по пространству | ❌ |
| GET | `/api/documents?status=Published` | Фильтрация по статусу | ❌ |
| POST | `/api/documents/search` | Поиск документов по названию/тексту с фильтрацией по дате | ❌ |

**Данные для SSR-страницы:**
- Список последних документов (title, author, updatedAt, status)
- Иерархическое дерево пространств (sidebar)
- Текущие права пользователя в активном пространстве

---

## Страница пространства (`GET /spaces/{id}`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/spaces/{id}` | Детали пространства (name, description, ownerId) | ❌ |
| GET | `/api/spaces/{id}/documents?page=0&size=20` | Документы в пространстве с пагинацией | ❌ |
| GET | `/api/spaces/{id}/tree` | Древовидная структура документов (для sidebar/TOC) | ❌ |
| GET | `/api/user/permissions?spaceId={id}` | Проверка прав текущего пользователя | ✅ |

---

## Страница просмотра документа (`GET /documents/{id}`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/{id}` | Полные данные документа (title, content, author, status, updatedAt, spaceId, templateId) | ❌ |
| GET | `/api/documents/{id}/attachments` | Список вложений документа | ❌ |
| GET | `/api/documents/{id}/permissions` | Права доступа к документу (поверх прав пространства) | ❌ |
| GET | `/api/documents/{id}/versions?page=0&size=10` | Список версий документа | ❌ |
| GET | `/api/user/permissions?spaceId={id}` | Права в пространстве (для отображения кнопок Edit/Delete) | ✅ |

**Данные для SSR-страницы:**
- Отрендеренный Wiki-текст (встроен в модель Thymeleaf)
- Метаданные: author, updatedAt, status (Draft/Published/Deleted)
- Список вложений (справая панель, опционально)

---

## Страница создания документа (`GET /documents/new`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/templates?role={role}` | Список шаблонов, доступных текущей роли | ❌ |
| GET | `/api/templates/{id}` | Содержимое шаблона (предзаполненный Markdown) | ❌ |
| GET | `/api/spaces` | Список доступных пространств (для выбора при создании) | ✅ |
| GET | `/api/user/permissions?spaceId={id}` | Проверка canCreate в выбранном пространстве | ✅ |
| POST | `/api/documents` | Создание документа (body: `{ title, content, spaceId, templateId, status }`) | ❌ |
| POST | `/api/blobs` | Загрузка вложения (multipart/form-data) → `{ url, id }` | ❌ |

**Формат создания (POST /api/documents):**
```json
{
  "title": "Название документа",
  "content": "# Заголовок\nСодержимое в Wiki-разметке...",
  "spaceId": 1,
  "templateId": 2,
  "status": "Draft"
}
```

---

## Страница редактирования документа (`GET /documents/{id}/edit`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/{id}` | Текущее содержимое документа для редактора | ❌ |
| PUT | `/api/documents/{id}` | Обновление документа (создаёт новую версию в Git) | ❌ |
| POST | `/api/blobs` | Загрузка вложения (multipart/form-data) → `{ url, id }` | ❌ |
| DELETE | `/api/blobs/{id}` | Удаление вложения | ❌ |
| GET | `/api/documents/{id}/attachments` | Список текущих вложений | ❌ |
| PUT | `/api/documents/{id}/permissions` | Настройка прав доступа к документу (поверх пространственных) | ❌ |
| PATCH | `/api/documents/{id}/status` | Изменение статуса (Draft → Published) | ❌ |
| DELETE | `/api/documents/{id}` | Soft-удаление документа (статус → Deleted) | ❌ |

**Формат обновления (PUT /api/documents/{id}):**
```json
{
  "title": "Обновлённое название",
  "content": "Новое содержимое...",
  "comment": "Комментарий к версии"
}
```

---

## Страница истории версий (`GET /documents/{id}/history`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/{id}/versions?page=0&size=20` | Список версий с пагинацией (gitHash, author, comment, createdAt) | ❌ |
| GET | `/api/documents/{id}/versions/{gitHash}` | Содержимое конкретной версии | ❌ |
| GET | `/api/documents/{id}/diff?from={hash1}&to={hash2}` | Сравнение двух версий (diff) | ❌ |
| POST | `/api/documents/{id}/restore/{gitHash}` | Откат к версии (создаёт новую версию-копию) | ❌ |

**Формат ответа версии:**
```json
{
  "id": 1,
  "gitHash": "a1b2c3d",
  "author": { "id": 1, "login": "admin" },
  "comment": "Обновление архитектуры",
  "createdAt": "2026-04-20T14:00:00Z",
  "content": "# Заголовок\nСодержимое версии..."
}
```

---

## Страница поиска (`GET /search?q=...`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/search?q=запрос&page=0&size=20` | Поиск по названию | ❌ |
| GET | `/api/documents/search?q=запрос&spaceId={id}` | Поиск в конкретном пространстве | ❌ |
| GET | `/api/documents/search?q=запрос&dateFrom=...&dateTo=...` | Поиск + фильтрация по дате | ❌ |
| GET | `/api/documents/search?q=запрос&status=Published` | Поиск + фильтрация по статусу | ❌ |

**Формат ответа поиска:**
```json
{
  "totalElements": 42,
  "totalPages": 3,
  "page": 0,
  "size": 20,
  "content": [
    {
      "id": 1,
      "title": "Название документа",
      "spaceId": 1,
      "spaceName": "Backend",
      "author": "admin",
      "updatedAt": "2026-04-20T14:00:00Z",
      "status": "Published"
    }
  ]
}
```

---

## Экспорт (не отдельная страница, действие на странице документа)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/{id}/export?format=pdf` | Экспорт в PDF (возвращает файл) | ❌ |
| GET | `/api/documents/{id}/export?format=docx` | Экспорт в DOCX (возвращает файл) | ❌ |
| GET | `/api/documents/{id}/export?format=html` | Экспорт в HTML (возвращает файл) | ❌ |
| GET | `/api/documents/{id}/export?format=html&includeAttachments=true` | Экспорт с вложениями (архив) | ❌ |

---

## Корзина (`GET /admin/trash`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/soft-deleted?page=0&size=20` | Список удалённых документов (is_deleted=true) | ❌ |
| PUT | `/api/documents/{id}/restore` | Восстановление из корзины (Deleted → Published) | ❌ |
| DELETE | `/api/documents/{id}/hard` | Физическое удаление (только ADMIN) | ❌ |
| DELETE | `/api/documents/{id}/purge` | Физическое удаление (алиас для /hard) | ❌ |

---

## Админ-панель: Пользователи (`GET /admin/users`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/admin/users?page=0&size=20&sortBy=login&sortDir=asc` | Список пользователей с пагинацией | ✅ |
| GET | `/api/admin/users?role=EDITOR` | Фильтрация по роли | ✅ |
| GET | `/api/admin/users?search=admin` | Поиск по логину/email | ❌ (search не реализован в бэкенде) |
| GET | `/api/admin/users/{id}` | Детали пользователя | ✅ |
| POST | `/api/admin/users` | Создание пользователя (`{ login, email, password, role }`) | ✅ |
| PUT | `/api/admin/users/{id}` | Обновление (`{ login, email, role }`) | ✅ |
| DELETE | `/api/admin/users/{id}` | Удаление (409, если есть связанные данные) | ✅ |
| PUT | `/api/admin/users/{id}/password` | Сброс пароля (`{ newPassword }`) | ✅ |

---

## Админ-панель: Пространства (`GET /admin/spaces`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/admin/spaces` | Все пространства системы | ✅ |
| POST | `/api/admin/spaces` | Создание пространства (`{ name, description, ownerId }`) | ❌ (не реализовано в бэкенде, есть в спецификации) |
| PUT | `/api/admin/spaces/{id}` | Обновление пространства | ❌ |
| DELETE | `/api/admin/spaces/{id}` | Удаление пространства (RESTRICT если есть документы) | ❌ |
| POST | `/api/admin/spaces/{spaceId}/permissions` | Назначение прав (`{ userId, permissionType: READ|WRITE|OWNER }`) | ✅ |
| GET | `/api/admin/spaces/{id}/permissions` | Список прав пространства | ❌ |

---

## Админ-панель: Настройки (`GET /admin/settings`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/settings` | Системные настройки (тема, язык) | ❌ |
| PUT | `/api/settings` | Обновление настроек | ❌ |

> **Примечание:** Страница настроек не описана в базовой документации, но упомянутa в readme.md как часть админ-панели. Требуется уточнение требований.

---

## Сводная таблица: Что нужно реализовать

### Критичные блоки (без них основной функционал не работает)

| Блок | Эндпоинты | Статус |
|------|-----------|--------|
| **Документы CRUD** | GET/POST/PUT/DELETE `/api/documents` | ❌ |
| **Документы — просмотр** | GET `/api/documents/{id}` | ❌ |
| **Документы — создание** | POST `/api/documents` + шаблоны | ❌ |
| **Документы — редактирование** | PUT `/api/documents/{id}` | ❌ |
| **Документы — удаление** | DELETE `/api/documents/{id}` (soft) | ❌ |
| **Версии** | GET `/api/documents/{id}/versions` | ❌ |
| **Diff версий** | GET `/api/documents/{id}/diff` | ❌ |
| **Откат версии** | POST `/api/documents/{id}/restore` | ❌ |
| **Поиск** | GET `/api/documents/search` | ❌ |
| **Вложения** | POST/DELETE `/api/blobs`, GET `/api/documents/{id}/attachments` | ❌ |
| **Экспорт** | GET `/api/documents/{id}/export` | ❌ |
| **Шаблоны** | GET `/api/templates` | ❌ |

### Важные (дополнительный функционал)

| Блок | Эндпоинты | Статус |
|------|-----------|--------|
| **Корзина** | GET `/api/documents/soft-deleted`, restore, hard-delete | ❌ |
| **Дерево документов** | GET `/api/spaces/{id}/tree` | ❌ |
| **Права к документу** | GET/PUT `/api/documents/{id}/permissions` | ❌ |
| **Пространства CRUD** | PUT/DELETE `/api/admin/spaces` | ❌ |
| **Поиск пользователей** | search param в `/api/admin/users` | ❌ |

### Уже реализовано

| Блок | Эндпоинты | Статус |
|------|-----------|--------|
| **Auth + Logout** | POST `/api/auth/login`, GET `/api/auth/me`, POST `/logout` | ✅ |
| **Users CRUD** | Full CRUD `/api/admin/users` + password | ✅ |
| **Spaces list + create** | GET/POST `/api/spaces`, GET `/api/admin/spaces` | ✅ |
| **Permissions** | POST `/api/admin/spaces/{id}/permissions`, GET `/api/user/permissions` | ✅ |

---

## Справочник: Глобальные роли и права

### Роли (GlobalRole)
| Роль | Описание |
|------|----------|
| `ADMIN` | Полный доступ ко всему, видит все пространства |
| `EDITOR` | Создание и редактирование документов с правом WRITE в пространстве |
| `READER` | Только чтение с правом READ в пространстве |

### Типы прав (PermissionType)
| Тип | Описание |
|-----|----------|
| `READ` | Чтение документов в пространстве |
| `WRITE` | Создание и редактирование документов |
| `OWNER` | Полный контроль + управление правами |

### Статусы документа (DocumentStatus)
| Статус | Описание |
|--------|----------|
| `Draft` | Черновик, виден только автору и админам |
| `Published` | Опубликован, доступен пользователям с правами |
| `Deleted` | Soft-удалён, виден только в корзине |

---

## Справочник: Шаблонные типы документов

### Для разработчика
| Шаблон | Описание |
|--------|----------|
| `architecture` | Описание архитектуры |
| `libraries` | Используемые библиотеки (name, type, license) |
| `dev-environment` | Настройки среды разработки |

### Для аналитика
| Шаблон | Описание |
|--------|----------|
| `business-process` | Описание бизнес-процессов |
| `requirements` | Требования (number auto, name, description .md, files) |
| `user-instruction` | Пользовательская инструкция |

### Для администратора
| Шаблон | Описание |
|--------|----------|
| `system-config` | Конфигурация (environments, params) |
| `environments` | Доступные среды |
| `known-issues` | Известные проблемы |
| `typical-queries` | Типовые запросы (category, type, description .md, files) |
