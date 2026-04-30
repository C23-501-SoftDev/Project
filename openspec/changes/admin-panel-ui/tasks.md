## 1. Frontend: CSS и общий JS модуль

- [x] 1.1 Извлечь CSS из прототипа `../../Docs/documents/prototypes/admin-panel/index.html` → сохранить в `backend/src/main/resources/static/css/admin-panel.css`
- [x] 1.2 Адаптировать CSS под Thymeleaf структуру: убедиться что классы `.layout`, `.sidebar`, `.header`, `.content`, `.navbar` корректно работают
- [x] 1.3 Добавить CSS для вкладок sidebar (состояние `.active`) из прототипа
- [x] 1.4 Создать `backend/src/main/resources/static/js/admin-common.js` с общими утилитами
- [x] 1.5 Реализовать `getCsrfToken()` — извлекает XSRF-TOKEN из document.cookie
- [x] 1.6 Реализовать `adminFetch(url, options)` — обёртка fetch с заголовком X-XSRF-TOKEN, обработка ошибок
- [x] 1.7 Реализовать `showToast(message, type)` — уведомление, автоматическое скрытие через 2с
- [x] 1.8 Реализовать `openModal(id)` / `closeModal(id)` — показ/скрытие overlay, закрытие по клику на overlay + Escape
- [x] 1.9 Реализовать `escapeHtml(str)` — предотвращение XSS

## 2. Frontend: Admin layout (полная замена заглушки)

- [x] 2.1 Переписать `admin-layout.html` по структуре прототипа: sidebar с элементами `.sidebar-tab`, navbar с breadcrumbs, content placeholder
- [x] 2.2 Подключить `admin-panel.css` вместо/вместе с `main.css`
- [x] 2.3 Подключить `admin-common.js`
- [x] 2.4 Добавить три вкладки sidebar: "Пользователи" → `/admin/users`, "Пространства" → `/admin/spaces`, "Настройки" → `/admin/settings`
- [x] 2.5 Реализовать подсветку активной вкладки через `th:class="${activePage == 'users' ? 'sidebar-tab active' : 'sidebar-tab'}"` или аналог
- [x] 2.6 Navbar: breadcrumbs "Администрирование > [текущая страница]", логин пользователя, форма выхода

## 3. Frontend: Admin Users page (полная замена заглушки)

- [x] 3.1 Полностью переписать `admin-users.html` по структуре прототипа: `<h1>Пользователи</h1>`, кнопка "+ Создать пользователя", панель фильтров (поиск + role checkboxes), таблица `#usersTable`, пагинация
- [x] 3.2 Подключить `admin-common.js` (в дополнение к CSS из layout)
- [x] 3.3 Inline `<script>`: fetch при загрузке страницы → GET `/api/admin/users?page=0&size=20` → render строк таблицы (адаптация renderUsersTable из прототипа)
- [x] 3.4 Сортировка столбцов: клик по заголовку → fetch с параметрами sortBy/sortDir → перерисовка
- [x] 3.5 Пагинация: кнопки Prev/Next, текст с информацией о странице, выбор количества на странице → fetch с новыми параметрами page/size
- [x] 3.6 Клиентский поисковой фильтр: поиск по login/email → фильтрация строк таблицы
- [x] 3.7 Role filter checkboxes (Admin/Editor/Reader) → кнопка Apply → фильтрация строк таблицы
- [x] 3.8 Модальное окно Create User: HTML структура из прототипа (#userModal) → POST `/api/admin/users` → обновление таблицы. Role как select (READER/EDITOR/ADMIN), статус назначается бэкендом
- [x] 3.9 Модальное окно Edit User: fetch `/api/admin/users/{id}` при открытии → заполнение полей (login, email, role select) → PUT при сохранении → обновление таблицы
- [x] 3.10 Сброс пароля в модальном окне Edit: если поле пароля заполнено → PUT `/api/admin/users/{id}/password`
- [x] 3.11 Модальное окно подтверждения Delete User (#deleteModal): DELETE `/api/admin/users/{id}` → обработка ошибки 409 conflict
- [x] 3.12 Бейджи: статус (Active=зелёный, Inactive=красный), роли (Admin=синий, Editor=зелёный, Reader=жёлтый)

## 4. Frontend: Admin Spaces page

- [x] 4.1 Полностью переписать `admin-spaces.html` по структуре прототипа: `<h1>Пространства</h1>`, кнопка "+ Создать пространство", таблица `#spacesTable`, пагинация
- [x] 4.2 Inline `<script>`: fetch при загрузке страницы → GET `/api/admin/spaces?page=0&size=20` → render строк таблицы
- [x] 4.3 Элементы управления пагинацией → fetch с новыми параметрами page/size
- [x] 4.4 Модальное окно Create Space (#spaceModal): получение списка пользователей для выпадающего списка Owner → POST `/api/admin/spaces` → обновление
- [x] 4.5 Модальное окно Manage Permissions: получение списка пользователей → выбор пользователя + тип → POST `/api/admin/spaces/{id}/permissions` → обновление
- [x] 4.6 Кнопка Edit Space → показать toast "Функция в разработке" (заглушка)
- [x] 4.7 Кнопка Delete Space → показать toast "Функция в разработке" (заглушка)
- [x] 4.8 Просмотр списка permissions → GET `/api/admin/spaces/{id}/permissions` + отображение userLogin и permissionType
- [x] 4.9 Кнопка Revoke permission → заглушка (backend endpoint ещё не доступен)
- [x] 4.10 Бейджи статуса (Active/Inactive) для таблицы пространств

## 5. Frontend: Admin Settings stub page

- [x] 5.1 Создать `admin-settings.html` Thymeleaf fragment с наследованием `admin-layout.html`
- [x] 5.2 Добавить сообщение "Функция в разработке" по центру с соответствующим стилем

## 6. Integration & Testing

- [x] 6.1 Ручное тестирование: убедиться, что admin-layout.html отображается с корректными вкладками sidebar, CSS загружается из стилей прототипа
- [x] 6.2 Ручное тестирование: перейти на `/admin/users` как Admin → убедиться, что таблица загружается из API, пагинация работает
- [x] 6.3 Ручное тестирование: создать/редактировать/удалить пользователя через модальные окна → убедиться, что API вызовы и обновления UI работают
- [x] 6.4 Ручное тестирование: перейти на `/admin/spaces` как Admin → убедиться, что таблица загружается, создание пространства работает
- [x] 6.5 Ручное тестирование: предоставить permission для пространства → убедиться, что API вызов выполняется успешно
- [x] 6.6 Ручное тестирование: убедиться, что кнопки Edit/Delete для пространства показывают заглушку "В разработке"
- [x] 6.7 Ручное тестирование: перейти на `/admin/settings` как Admin → убедиться, что страница-заглушка отображается
- [x] 6.8 Ручное тестирование: убедиться, что роли Editor/Reader получают 403 на страницах `/admin/*`
- [x] 6.9 Запустить `mvn clean compile` из директории backend — убедиться в отсутствии ошибок компиляции
- [x] 6.10 Запустить `mvn test` из директории backend — убедиться, что все тесты проходят

## 7. Documentation

- [x] 7.1 Обновить заголовок даты в BACKTRACKER.md на текущую дату
- [x] 7.2 Проверка критериев качества — выполнить все шаги из `openspec/quality-gates.md`