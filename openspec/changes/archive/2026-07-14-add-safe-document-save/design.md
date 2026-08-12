# Design: add-safe-document-save

## Context

Проект использует стек:
- **Backend**: Spring Security 6 с CSRF-защитой через Cookie-to-Header токен (`XSRF-TOKEN` → `X-XSRF-TOKEN`).
- **Frontend**: Thymeleaf-шаблоны + vanilla JavaScript с утилитами `apiFetch()` и `getCsrfToken()` из `main.js`.
- **Аутентификация**: JWT хранится в HttpOnly Cookie (`JWT`), браузер отправляет его автоматически при `credentials: 'same-origin'`.

Текущая реализация `document-edit.html` имеет базовую функцию `saveDocument()`, которая использует `apiFetch`, но не обрабатывает явно статусы 401 и 403, не имеет автосохранения и индикатора состояния.

## Goals / Non-Goals

**Goals:**
- Явная обработка HTTP-ответов 200 / 400 / 401 / 403 в клиентском коде.
- Добавление debounce-автосохранения (3 сек после остановки ввода) для полей `title` и `content`.
- Индикатор состояния сохранения («Сохранено», «Несохранённые изменения», «Сохранение...», «Ошибка сохранения»).
- Переиспользование существующих утилит безопасности (`getCsrfToken`, `apiFetch`).

**Non-Goals:**
- Изменения в бэкенд-API (`PUT /api/documents/{id}` уже реализован).
- Конфликт-резолюция при конкурентном редактировании (будет в отдельной фиче).
- Хранение несохранённого черновика в `localStorage` (может быть добавлено позже).
- Изменение логики `apiFetch` (она уже корректно обрабатывает CSRF и работает с `credentials: 'same-origin'`).

## Decisions

### 1. Архитектура клиентской части

**Модульная организация кода в `document-edit.html`:**

```
DocumentEditController (IIFE-замыкание)
├── State
│   ├── documentLoaded: boolean
│   ├── autosaveTimer: number | null
│   ├── isSaving: boolean
│   └── currentDoc: object | null
├── DOM References
│   ├── textarea, titleField, statusField, saveBtn
│   └── autosaveStatus (новый элемент)
├── Core Functions
│   ├── loadDocument() — загрузка с API, установка documentLoaded = true
│   ├── saveDocument(options?: { silent: boolean }) — PUT-запрос с обработкой статусов
│   ├── setupAutosave() — регистрация обработчиков input + debounce
│   └── updateAutosaveStatus(state: string) — обновление UI индикатора
└── Event Handlers
    ├── saveBtn.click → saveDocument()
    ├── Ctrl+S → saveDocument()
    └── textarea/titleField.input → debounce → saveDocument({ silent: true })
```

### 2. Обработка статусов ответа

**`saveDocument(options)` — алгоритм:**

```javascript
async function saveDocument(options = {}) {
  const silent = options.silent || false;
  
  // 1. Клиентская валидация
  if (!title.trim()) {
    showToast('Заголовок не может быть пустым', 'error');
    return;
  }
  
  // 2. Защита от двойной отправки
  if (isSaving) return;
  isSaving = true;
  updateAutosaveStatus('saving');
  
  try {
    // 3. Отправка через apiFetch (включает CSRF-токен и credentials)
    const result = await apiFetch(`/api/documents/${docId}`, {
      method: 'PUT',
      body: JSON.stringify({ title, status, content })
    });
    
    // 4. Успех (200 OK)
    if (!silent) {
      showToast('Документ сохранён');
    }
    updateAutosaveStatus('saved');
    
  } catch (error) {
    // 5. Обработка ошибок
    if (error.status === 401) {
      // Сессия истекла → редирект на login
      window.location.href = '/login';
      return;
    }
    
    if (error.status === 403) {
      // CSRF-токен устарел
      showToast('Ошибка безопасности. Обновите страницу.', 'error');
      updateAutosaveStatus('error');
      return;
    }
    
    if (error.status === 400) {
      // Ошибки валидации (apiFetch уже парсит fieldErrors)
      showToast(error.message, 'error');
      updateAutosaveStatus('error');
      return;
    }
    
    // 6. Прочие ошибки
    showToast(error.message || 'Ошибка сохранения', 'error');
    updateAutosaveStatus('error');
    
  } finally {
    isSaving = false;
  }
}
```

**Замечание**: `apiFetch()` в текущей реализации (`main.js`) уже парсит `response.status` и формирует `Error` с полем `message`. Мы добавим в `apiFetch()` поле `error.status`, чтобы клиентский код мог различать статусы (изменение утилиты).

### 3. Автосохранение с debounce

**`setupAutosave()` — алгоритм:**

```javascript
function setupAutosave() {
  const fields = [textarea, titleField];
  
  fields.forEach(field => {
    field.addEventListener('input', () => {
      // Проверка, что документ загружен
      if (!documentLoaded) return;
      
      // Сброс предыдущего таймера
      if (autosaveTimer) {
        clearTimeout(autosaveTimer);
      }
      
      // Индикация несохранённых изменений
      updateAutosaveStatus('unsaved');
      
      // Запуск нового таймера (3 сек)
      autosaveTimer = setTimeout(() => {
        saveDocument({ silent: true });
      }, 3000);
    });
  });
}
```

### 4. Индикатор состояния сохранения

**HTML-элемент (добавляется в `editor-header` рядом с кнопкой «Сохранить»):**

```html
<span id="autosaveStatus" class="autosave-status" data-state="saved">
  Сохранено
</span>
```

**CSS (добавляется в `<style>` или `main.css`):**

```css
.autosave-status {
  font-size: 13px;
  color: #6B7280;
  font-weight: 500;
  transition: color 0.15s ease;
}

.autosave-status[data-state="saved"] {
  color: #059669;
}

.autosave-status[data-state="unsaved"] {
  color: #D97706;
}

.autosave-status[data-state="saving"] {
  color: #3B82F6;
}

.autosave-status[data-state="error"] {
  color: #DC2626;
}
```

**JavaScript-функция обновления:**

```javascript
function updateAutosaveStatus(state) {
  const statusEl = document.getElementById('autosaveStatus');
  if (!statusEl) return;
  
  statusEl.setAttribute('data-state', state);
  
  const messages = {
    saved: 'Сохранено',
    unsaved: 'Несохранённые изменения',
    saving: 'Сохранение...',
    error: 'Ошибка сохранения'
  };
  
  statusEl.textContent = messages[state] || '';
}
```

### 5. Доработка утилиты `apiFetch` (main.js)

**Проблема:** Текущая реализация `apiFetch` выбрасывает `Error` с `message`, но не сохраняет статус ответа.

**Решение:** Добавить в объект ошибки поле `status`:

```javascript
async function apiFetch(url, options = {}) {
  // ... существующий код ...
  
  try {
    const response = await fetch(url, defaultOptions);
    
    if (!response.ok) {
      const error = new Error(errorMessage); // errorMessage формируется как раньше
      error.status = response.status; // ← НОВОЕ: сохраняем статус
      throw error;
    }
    
    // ... остальной код ...
  } catch (error) {
    // ... существующая обработка ...
    throw error;
  }
}
```

## Risks / Trade-offs

### Risk 1: Гонка между ручным сохранением и автосохранением

**Scenario:** Пользователь нажимает «Сохранить» в момент, когда запущен таймер автосохранения.

**Mitigation:**
- Флаг `isSaving` блокирует повторные вызовы `saveDocument()` до завершения текущего запроса.
- При клике на кнопку таймер автосохранения сбрасывается (`clearTimeout(autosaveTimer)`).

### Risk 2: Потеря несохранённых изменений при закрытии вкладки

**Scenario:** Пользователь закрывает вкладку во время таймера debounce (до отправки автосохранения).

**Mitigation (не входит в Goals, но можно добавить позже):**
- Обработчик `window.addEventListener('beforeunload', ...)` с проверкой флага `hasUnsavedChanges`.
- Если `hasUnsavedChanges === true`, показать браузерное предупреждение.

**Trade-off:** Не реализуем в рамках этого change, чтобы не перегружать задачу.

### Risk 3: Старый CSRF-токен после долгого бездействия

**Scenario:** Пользователь открыл страницу редактирования, не взаимодействовал 30 минут, затем нажал «Сохранить».

**Mitigation:**
- Сервер (Spring Security) вернёт `403 Forbidden`.
- Клиент покажет сообщение «Ошибка безопасности. Обновите страницу.»
- После обновления страницы браузер получит новый `XSRF-TOKEN` из Cookie.

**Trade-off:** Не реализуем автоматическую ротацию токена (это требует дополнительного эндпоинта `/api/csrf` и усложняет логику).

## Proposed Code Changes

### 1. Модификация `main.js`

**Файл:** `backend/src/main/resources/static/js/main.js`

**Изменение:**
```javascript
// В функции apiFetch, после блока if (!response.ok):
const error = new Error(errorMessage);
error.status = response.status; // ← добавить эту строку
throw error;
```

### 2. Обновление `document-edit.html`

**Файл:** `backend/src/main/resources/templates/pages/document-edit.html`

**Изменения:**

1. **Добавление индикатора состояния в HTML (в `.editor-actions`):**
   ```html
   <span id="autosaveStatus" class="autosave-status" data-state="saved">Сохранено</span>
   ```

2. **Добавление CSS-стилей для индикатора (в `<style>`):**
   ```css
   .autosave-status { ... }
   ```

3. **Рефакторинг `<script>` блока:**
   - Добавить переменные состояния: `documentLoaded`, `autosaveTimer`, `isSaving`.
   - Переписать `saveDocument()` с обработкой статусов 401/403/400 через `error.status`.
   - Добавить функцию `updateAutosaveStatus(state)`.
   - Добавить функцию `setupAutosave()` и вызвать её после `loadDocument()`.
   - В `loadDocument()` установить `documentLoaded = true` после успешной загрузки.

### 3. Добавление CSS-стилей (опционально в отдельный файл)

**Если стили будут вынесены в `backend/src/main/resources/static/css/main.css`:**

```css
.autosave-status {
  font-size: 13px;
  color: #6B7280;
  font-weight: 500;
  transition: color 0.15s ease;
}

.autosave-status[data-state="saved"] {
  color: #059669;
}

.autosave-status[data-state="unsaved"] {
  color: #D97706;
}

.autosave-status[data-state="saving"] {
  color: #3B82F6;
}

.autosave-status[data-state="error"] {
  color: #DC2626;
}
```

## Verification Plan

### Manual Testing

1. **Тест 401 Unauthorized:**
   - Открыть страницу редактирования документа.
   - Вручную удалить Cookie `JWT` через DevTools → Application → Cookies.
   - Нажать «Сохранить» или подождать автосохранения.
   - Ожидаемый результат: редирект на `/login`.

2. **Тест 403 Forbidden (CSRF):**
   - Открыть страницу редактирования.
   - Вручную удалить Cookie `XSRF-TOKEN`.
   - Нажать «Сохранить».
   - Ожидаемый результат: сообщение «Ошибка безопасности. Обновите страницу.»

3. **Тест 400 Bad Request (валидация):**
   - Очистить поле «Название» (оставить пустым).
   - Нажать «Сохранить».
   - Ожидаемый результат: сообщение «Заголовок не может быть пустым» (клиентская валидация).
   - Альтернативно: отключить клиентскую валидацию → ожидать серверную ошибку с `fieldErrors`.

4. **Тест автосохранения (debounce):**
   - Открыть документ, начать вводить текст в textarea.
   - Остановить ввод на 3 секунды.
   - Ожидаемый результат: индикатор показывает «Сохранение...», затем «Сохранено».
   - Открыть Network DevTools → проверить PUT-запрос с `X-XSRF-TOKEN`.

5. **Тест сброса таймера (rapid input):**
   - Начать вводить текст, продолжать без пауз в течение 5 секунд.
   - Ожидаемый результат: запрос НЕ отправляется до остановки ввода на 3 сек.

6. **Тест индикатора состояния:**
   - Загрузить документ → индикатор «Сохранено».
   - Изменить текст → индикатор «Несохранённые изменения».
   - Подождать 3 сек → индикатор «Сохранение...» → «Сохранено».
   - Симулировать ошибку (удалить CSRF) → индикатор «Ошибка сохранения».

### Integration Testing (опционально)

- Playwright/Cypress тест:
  - Логин → открыть редактирование документа.
  - Изменить title/content.
  - Проверить наличие заголовка `X-XSRF-TOKEN` в PUT-запросе (через Network перехват).
  - Симулировать 401 (через Mock Service Worker или перехват) → проверить редирект.

## Open Questions

1. **Q:** Нужно ли сохранять черновик в `localStorage` как резервную копию?
   **A:** Не в рамках этого change. Можно добавить в отдельной фиче `local-draft-backup`.

2. **Q:** Как обрабатывать конфликты (если документ изменён другим пользователем)?
   **A:** Не в рамках этого change. Требует добавления `version` поля и проверки через `ETag` / `If-Match`.

3. **Q:** Должен ли индикатор показывать timestamp последнего сохранения?
   **A:** Опционально. Можно добавить формат «Сохранено в 14:32», но это усложняет UI. Текущий дизайн ограничивается состояниями.
