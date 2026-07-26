# Design: URL-параметры для фильтров

## 1. Общий подход

Все фильтры хранятся в глобальном объекте `state`. При изменении любого фильтра или переключении страницы вызывается `updateUrl()`, которая сериализует актуальное состояние в URL через `history.replaceState`. При загрузке страницы состояние восстанавливается из `URLSearchParams`. 

**Важно:** URL не содержит параметры, равные значениям по умолчанию (все статусы, сортировка по id/desc, page=0), что держит URL чистым и коротким.

## 2. URL-схема

```
/?page={number}&spaceId={id}&authorId={id}&q={search}&status={Draft,Published,Deleted}&sortBy={field}&sortDir={asc|desc}
```

### Правила сериализации:

| Параметр | Условие добавления | Пример |
|---|---|---|
| `page` | `state.page > 0` | `page=2` |
| `spaceId` | `state.spaceFilter` не пуст | `spaceId=5` |
| `authorId` | `state.authorFilter` не пуст | `authorId=3` |
| `q` | `state.searchTerm` не пуст | `q=hello` |
| `status` | `0 < state.statusFilters.length < 3` | `status=Draft,Published` |
| `sortBy` | `state.sortBy !== 'id'` | `sortBy=title` |
| `sortDir` | `state.sortDir !== 'desc'` | `sortDir=asc` |

## 3. Поток данных

### Применение фильтров (applyFilters)
1. Сбор значений из DOM (чекбоксы статусов, select пространства, select автора)
2. Обновление `state`
3. Сброс `state.page = 0`
4. Вызов `fetchDocuments()` (запрос на сервер)
5. Вызов `updateUrl()` (обновление URL)

### Очистка фильтров (clearFilters)
1. Сброс `state` к значениям по умолчанию
2. Сброс DOM-элементов (чекбоксы все отмечены, селекты пусты)
3. Вызов `fetchDocuments()`
4. Вызов `updateUrl()`

### Переключение страниц (prev/next)
1. Изменение `state.page`
2. Вызов `fetchDocuments()`
3. Вызов `updateUrl()`

### Загрузка страницы (DOMContentLoaded)
1. Чтение `URLSearchParams`
2. Инициализация `state` из URL
3. Синхронизация чекбоксов статусов
4. Восстановление имени пространства из `localStorage`
5. Инициализация кастомных селектов (`setupCustomSelect`)
6. **Асинхронная** загрузка опций для селектов (`await fetchFilters()`)
7. Синхронизация значений селектов с `state` (текст в `.select-styled`, значение в скрытом `<select>`)
8. Вызов `fetchDocuments()` и `checkAdminStatus()`

### Переход через дерево пространств (toggleSpaceTree, spaceSelected)
- `toggleSpaceTree`: копирует текущий `URLSearchParams`, переопределяет `spaceId`, удаляет `page`, редиректит на `/?<params>`
- `spaceSelected` (на главной): обновляет `state.spaceFilter`, сбрасывает `page`, синхронизирует кастомный селект с названием пространства, вызывает `fetchDocuments()` и `updateUrl()`

## 4. Обработка краевых случаев

| Сценарий | Поведение |
|---|---|
| В URL передан пустой `status` | Устанавливаются все статусы: `['Draft', 'Published', 'Deleted']` |
| В URL передан невалидный `status` (например, `status=Invalid`) | Устанавливается как есть, чекбокс не найдётся — фильтр не сработает |
| Параметр отсутствует в URL | Используется значение по умолчанию (page=0, все статусы, sortBy=id, sortDir=desc, без фильтрации) |
| `sortBy` или `sortDir` отсутствуют в URL | Используются значения по умолчанию (`id`, `desc`) |
| Выбраны все 3 статуса | Параметр `status` не добавляется в URL (чище и короче) |
| Выбраны 1 или 2 статуса | Параметр `status` добавляется с разделителем `,` |

## 5. Безопасность и ограничения

- Изменения только на frontend — никакие новые эндпоинты не добавляются
- `history.replaceState` не создаёт новые записи в истории браузера, поэтому кнопка "Назад" не приведёт к зацикливанию
- Все значения из URL экранируются через `URLSearchParams` (автоматическое кодирование спецсимволов)
- Валидация значений фильтров происходит на сервере (`fetchDocuments` отправляет их в API)

## 6. Изменяемые функции/методы

| Функция | Тип изменений |
|---|---|
| `toggleSpaceTree()` | Изменение (сохранение URL-параметров) |
| `updateUrl()` | Расширение (добавление authorId, status, sortBy, sortDir) |
| `applyFilters()` | Дополнение (вызов updateUrl) |
| `clearFilters()` | Дополнение (вызов updateUrl) |
| `spaceSelected` listener | Дополнение (синхронизация кастомного селекта) |
| `DOMContentLoaded` listener | Переработка (асинхронная загрузка, полное восстановление) |