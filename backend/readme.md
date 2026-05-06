# База Знаний

Проект по систематизации и управлению требованиями, процессами и бэклогом.

## Установка и настройка окружения

### 1. Кому что нужно устанавливать (памятка для команды)

| Роль | Необходимые программы |
|------|----------------------|
| **Все** | Git, браузер (Chrome/Firefox/Edge), live-server (Node.js) |
| **Backend-разработчик** | Java 17 или 21 JDK, Maven 3.9.14, PostgreSQL 18 |
| **Frontend-разработчик** | Node.js |
| **Тестировщик (QA)** | Браузер, Postman(или альтернатива, например, Insomnia, pgAdmin) |
| **Аналитик** | Только общий набор |
| **Техлид** | Всё вышеперечисленное |

### 2. Node.js, npm

#### Установка Node.js (версия 18+)

#### Установка Node.js (версия 18+)

- Скачайте установщик с [nodejs.org](https://nodejs.org) (рекомендуется LTS-версия)

#### Проверка

#### Проверка

```bash
node -v
npm -v
```

### 3. Java 17/21 (JDK)

Для работы бэкенда необходима Java версии 17 или 21.

**Скачивание с официального сайта:**

1. Перейдите по ссылке:  
   [https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html]

2. Выберите установщик для вашей операционной системы:

   | ОС | Тип файла |
   |----|-----------|
   | Windows | `.exe` или `.msi` (x64 Installer) |
   | macOS | `.dmg` (Arm 64 или x64 в зависимости от процессора) |
   | Linux | `.tar.gz` (x64) |

3. Запустите скачанный файл и следуйте инструкциям установщика

**Проверка установки:**

Откройте командную строку (терминал) и выполните:

```bash
java -version
```

Вы должны увидеть что-то вроде:
java version "17.0.12" 2024-07-16 LTS
Java(TM) SE Runtime Environment (build 17.0.12+8-LTS-286)
Java HotSpot(TM) 64-Bit Server VM (build 17.0.12+8-LTS-286, mixed mode, sharing)

### 4. Maven (система сборки)

**Если используете IntelliJ IDEA и запускать проект планируете из неё, то этот пункт можно пропустить, эта IDE уже имеет встроенный maven**

Maven используется для управления зависимостями и сборки бэкенда.  
Устанавливается последняя стабильная версия (на текущий момент — **3.9.14**).

#### Windows

1. Скачайте архив `apache-maven-3.9.14-bin.zip` с официального сайта:  
   [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)

2. Распакуйте архив в папку, например: `C:\maven`

3. Добавьте `C:\maven\bin` в переменную окружения `PATH`:
   - Нажмите `Win + R`, введите `sysdm.cpl`
   - Перейдите на вкладку `Advanced` → `Environment Variables`
   - В разделе `System variables` найдите `Path`, нажмите `Edit`
   - Добавьте новую строку: `C:\maven\bin`
   - Нажмите `OK` во всех окнах

#### macOS

```bash
brew install maven
```

#### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install maven
```

#### Проверка после установки (для всех ОС)

#### Проверка после установки (для всех ОС)

```cmd
mvn -version
```

### 5. PostgreSQL

У большинства уже установлен через pgAdmin в рамках других дисциплин.  
Если нет — установите PostgreSQL 18 с официального сайта: <https://www.postgresql.org/download/>
Если нет — установите PostgreSQL 18 с официального сайта: <https://www.postgresql.org/download/>

#### Создание базы данных (через pgAdmin)

1. Откройте pgAdmin
2. Подключитесь к серверу
3. В окне Query Tools (Инструмент запросов) для базы postgres введите следующий SQL-запрос(создание пользователя для работы с БД):

```sql
CREATE USER kb_user WITH PASSWORD 'strong_password';
```

А затем этот (создание самой БД):

```sql
CREATE DATABASE knowledge_base
   WITH 
   OWNER = kb_user
   ENCODING = 'UTF8'
   CONNECTION LIMIT = -1;
```

## Запуск

В командной строке перейдите в директорию backend проекта и выполните:

```cmd
mvn clean compile
mvn spring-boot:run
```

После этого приложение соберётся и запустится.

## Локальная разработка в Docker (для команды)

### 🚀 Быстрый старт (One-Click Setup)

Самый простой способ — запустить автоматический bootstrap-скрипт:

```bash
# Способ 1: Из VS Code (рекомендуется)
# Cmd/Ctrl + Shift + P → Tasks: Run Task → "Dev: One Click Setup"

# Способ 2: Из терминала
make dev-up
```

Скрипт автоматически:

- ✅ Проверит Docker и Docker Compose
- ✅ Подготовит `.env` из `.env.example`
- ✅ Сгенерирует безопасный `JWT_SECRET_KEY`
- ✅ Установит `UID/GID` для macOS/Linux
- ✅ Поднимет контейнеры (`app` и `postgres`)
- ✅ Проверит здоровье приложения
- ✅ Выведет ссылки для доступа

**Обычно занимает 2-5 минут.**

### 📋 Структура проекта

```
Project/
├── backend/                    # Spring Boot приложение
│   └── src/main/java/com/knowledgebase/
│       ├── application/        # Spring компоненты, конфигурация
│       ├── domain/            # бизнес-логика, ентитеты
│       ├── infrastructure/     # репозитории, БД работа
│       └── interfaces/         # контроллеры REST API, Thymeleaf шаблоны
│   └── src/test/java/         # тесты бэкенда
│
├── docker-compose.yml          # Описание контейнеров
├── Makefile                    # Быстрые команды
├── .env.example                # Шаблон переменных окружения
└── scripts/dev/bootstrap.sh    # Автоматический setup
```

### 🔄 Режимы разработки

#### LOCAL (по умолчанию, рекомендуется)

**Как работает:**

- Код находится **на вашей машине** в папке `backend/`
- Docker контейнер **монтирует** эту папку (синхронизация live)
- Вы редактируете файлы в VS Code на хосте
- Spring Boot автоматически пересобирает код (10-30 сек)
- Изменения видны мгновенно после перезагрузки браузера

**Включить:**

```bash
# По умолчанию в .env:
SOURCE_MODE=local
```

**Как работать:**

1. Редактируйте Java-файлы в `backend/src/main/java`
2. Сохраните файл (Cmd+S / Ctrl+S)
3. Посмотрите логи: `make dev-logs` — видите пересборку
4. Обновите браузер на `http://localhost:8080`

**Преимущества:**

- ✅ Естественный workflow в IDE
- ✅ Изменения видны сразу
- ✅ Легко дебажить и ставить breakpoint'ы
- ✅ Git работает нормально

**Когда используется:**

- Локальная разработка (90% случаев)
- Отладка
- Быстрое прототипирование

#### SSH (расширенный режим)

**Как работает:**

- Код клонируется **внутри контейнера** при первом старте
- Вы редактируете файлы либо в контейнере (`make docker-shell`), либо через VS Code Remote SSH
- При остановке контейнера код может быть потерян, если не сделать commit/push

**Включить:**

```bash
# В .env:
SOURCE_MODE=ssh
GIT_REPO_URL=git@github.com:username/project.git
GIT_BRANCH=main
AUTO_PULL=false  # Или 'true' для auto-pull при каждом старте
```

**Требования для SSH:**

- Настроить SSH-агент на хосте:

  ```bash
  eval $(ssh-agent)
  ssh-add ~/.ssh/id_rsa  # или путь к вашему SSH-ключу
  ```

- Docker Desktop должен быть настроен на перенос SSH auth socket

**Как работать:**

1. `make docker-shell` — входите в контейнер
2. Редактируйте файлы (vi, nano, или подключите VS Code Remote)
3. При необходимости: `git commit` и `git push` внутри контейнера
4. Spring Boot пересобирает, изменения видны в браузере

**Преимущества:**

- ✅ Чистое окружение (без локального clone)
- ✅ Полезно для CI/CD пайпов
- ✅ Безопаснее для публичных окружений

**Когда используется:**

- Отладка SSH-related issue'ов
- Подготовка к deployment
- Чистое окружение для тестирования
- Когда нет локального clone

### Детальный manual setup (если bootstrap не сработает)

**Файлы Docker-конфигурации:**

- `docker-compose.yml` — описание сервисов (app, postgres, redis, rabbitmq)
- `.env.example` — шаблон переменных окружения
- `Makefile` — быстрые команды
- `backend/Dockerfile` — конфигурация образа приложения

**Шаг 1: Подготовить .env**

```bash
cp .env.example .env
```

**Шаг 2: Заполнить переменные окружения (macOS/Linux)**

```bash
echo "UID=$(id -u)" >> .env
echo "GID=$(id -g)" >> .env
```

**Шаг 3: Запустить контейнеры**

```bash
docker compose --env-file .env up -d --build
```

**Шаг 4: Проверить статус**

```bash
docker ps
```

Должны быть запущены контейнеры:

- `kb_app` (Spring Boot приложение)
- `kb_postgres` (база данных)

### 🛠️ Установка зависимостей и миграции

**Зависимости Maven:**
Подтягиваются автоматически при старте. Если нужно вручную:

```bash
docker compose --env-file .env exec app mvn -B dependency:resolve
```

**Liquibase миграции БД:**
Применяются автоматически при старте приложения. Ничего не нужно делать.

**Запуск тестов:**

```bash
docker compose --env-file .env exec app mvn test
```

### 📝 Команды для повседневной работы

| Команда | Что делает |
|---------|-----------|
| `make dev-up` | Быстрый старт приложения (автоматический setup) |
| `make dev-down` | Остановить контейнеры (данные сохраняются) |
| `make dev-logs` | Видеть live логи app и postgres |
| `make docker-shell` | Вход в контейнер app |
| `docker ps` | Статус всех контейнеров |
| `docker compose down -v` | Полное удаление (включая БД) |

### 🚨 Troubleshooting

#### Ошибка: `Docker is not installed`

**Решение:**

- Установите Docker Desktop: <https://www.docker.com/products/docker-desktop>
- Проверьте: `docker --version` должно работать

#### Ошибка: `Docker Compose is not installed`

**Решение:**

- Docker Desktop v4.0+ имеет встроенный Docker Compose
- Обновите Docker Desktop или установите Docker Compose отдельно

#### Ошибка: `permission denied (publickey)` при SSH mode

**Решение:**

1. Проверьте SSH-ключ: `ssh-add -l`
2. Добавьте ключ:

   ```bash
   eval $(ssh-agent)
   ssh-add ~/.ssh/id_rsa
   ```

3. Проверьте `GIT_REPO_URL` в `.env` — должен быть вида `git@github.com:username/repo.git`
4. Проверьте, что ключ добавлен в GitHub

#### Ошибка: `host key verification failed`

**Решение:**

1. Добавьте хост в `known_hosts`:

   ```bash
   ssh-keyscan -H github.com >> ~/.ssh/known_hosts
   ```

2. Перезапустите bootstrap: `make dev-up`

#### Порт 8080 уже занят

**Решение:**

- Найдите процесс: `lsof -i :8080` (macOS/Linux) или `netstat -ano | grep 8080` (Windows)
- Или измените в `.env`: `APP_PORT=8081`

#### Приложение не стартует, логи показывают `permission denied`

**Решение:**

1. Проверьте `UID` и `GID` в `.env`:

   ```bash
   echo UID=$(id -u)
   echo GID=$(id -g)
   ```

2. Перезапустите:

   ```bash
   make dev-down
   make dev-up
   ```

3. Если на Linux права испорчены:

   ```bash
   sudo chown -R "$(id -u):$(id -g)" ./backend/data
   ```

#### БД недоступна, приложение не стартует

**Решение:**

1. Проверьте, что оба контейнера запущены: `docker ps`
2. Посмотрите логи postgres: `docker compose logs postgres`
3. Проверьте `POSTGRES_*` переменные в `.env`
4. Перезапустите с очисткой БД:

   ```bash
   make dev-down
   docker volume rm kb_postgres_data  # Осторожно! Удалит БД
   make dev-up
   ```

#### Изменения в коде не видны после сохранения (LOCAL mode)

**Решение:**

1. Проверьте логи: `make dev-logs`
2. Убедитесь, что `SOURCE_MODE=local` в `.env`
3. Проверьте, что папка `backend/src` примонтирована: `docker inspect kb_app | grep -A 10 Mounts`
4. Перезапустите контейнер: `make dev-down && make dev-up`

#### `mvn clean` очень медленно работает в контейнере

**Решение:**
Это нормально. Maven кэш находится в томе `maven_cache`, который сохраняется между запусками. Первый запуск медленнее.

### ⏸️ Остановка и очистка

**Остановить контейнеры (данные сохраняются):**

```bash
make dev-down
```

**Полная очистка (удалит БД и кэш):**

```bash
make dev-down
docker volume rm kb_postgres_data kb_maven_cache  # Осторожно!
```

**Перезапустить отдельный сервис:**

```bash
docker compose restart app    # Только app
docker compose restart postgres  # Только БД
```

## 📖 Где работать после успешного запуска

После того как контейнеры запущены и приложение доступно по <http://localhost:8080>, вот рекомендуемый workflow:

### 1. Проверить, что всё работает

- Откройте <http://localhost:8080> в браузере
- Смотрите страницу входа (или главную, если уже авторизованы)
- Откройте <http://localhost:8080/actuator/health> — должна быть `"status":"UP"`

### 2. Понять структуру кода

```
backend/src/main/java/com/knowledgebase/
├── application/        # Spring компоненты, конфигурация, сервисы
├── domain/            # Бизнес-логика, ентитеты, интерфейсы репозиториев
├── infrastructure/    # Реализация репозиториев, БД работа, внешние сервисы
└── interfaces/        # Контроллеры REST API, обработчики Thymeleaf
```

### 3. Начать разработку

1. **Выбрать слой для изменений:**
   - API? → `interfaces/` (контроллеры)
   - Бизнес-логика? → `application/` (сервисы)
   - Новая сущность? → `domain/` (entity, repository interface)
   - Запросы в БД? → `infrastructure/` (repository impl)

2. **Редактировать файл:**
   - Откройте файл в VS Code
   - Сохраните (Cmd+S / Ctrl+S)
   - Смотрите логи: `make dev-logs`

3. **Дождитесь пересборки:**
   - Spring Boot обнаружит изменения (5-30 сек)
   - Логи покажут что-то вроде: `Restarting Spring Application...`

4. **Тестируйте:**
   - Обновите браузер (F5 / Cmd+R)
   - Используйте Swagger UI: <http://localhost:8080/swagger-ui.html>
   - Или проверьте логи для ошибок

### 4. Полезные ссылки внутри приложения

- **Главная страница:** <http://localhost:8080>
- **API документация (Swagger):** <http://localhost:8080/swagger-ui.html>
- **Проверка здоровья:** <http://localhost:8080/actuator/health>
- **Метрики приложения:** <http://localhost:8080/actuator/metrics> (если включены)

### 5. Commit и push

Когда готовы к commit:

```bash
git add .
git commit -m "feat: описание изменения"
git push origin feature/название
```

При SSH mode — коммитьте **внутри контейнера** перед остановкой:

```bash
make docker-shell
# Внутри контейнера:
cd /workspace && git add . && git commit -m "..." && git push
```

## 📑 Страницы приложения (SSR)

Приложение использует серверный рендеринг (Thymeleaf). Пользователь взаимодействует со страницами через браузер — авторизация, навигация по пространствам и документам.

### Публичные страницы

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/login` | Страница входа с формой аутентификации |
| POST | `/login` | Обработка формы — установка JWT Cookie и редирект на главную |
| POST | `/logout` | Выход — очистка Cookie и редирект на `/login` |

### Страницы авторизованных пользователей

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/` | Главная страница — список документов |
| GET | `/documents/{id}` | Просмотр документа |
| GET | `/documents/new` | Создание нового документа |
| GET | `/documents/{id}/edit` | Редактирование документа |
| GET | `/documents/{id}/history` | История версий документа |
| GET | `/search?q=...` | Результаты поиска |
| GET | `/spaces/{id}` | Страница пространства |

### Административные панели

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/admin/users` | Управление пользователями | ADMIN |
| GET | `/admin/spaces` | Управление пространствами | ADMIN |

Функции админ-панели (см. прототип `Docs/prototypes/admin-panel/index.html`):

- **Users** — таблица пользователей с сортировкой, фильтрацией по ролям (Admin/Editor/Reader), поиском по логину/email, пагинацией. Модальные окна для создания, редактирования и удаления пользователей.
- **Spaces** — таблица пространств с сортировкой, пагинацией. Создание, редактирование, удаление пространств.
- **Settings** — системные настройки (тема, язык).

## 🔌 REST API

Все API-эндпоинты описаны через OpenAPI (Swagger UI). Это основной способ изучения доступных методов, параметров и форматов ответов. **Для тестирования эндпоинтов рекомендуется использовать Swagger UI**, а не curl/Postman.

Swagger UI: **<http://localhost:8080/swagger-ui.html>**
Swagger UI: **<http://localhost:8080/swagger-ui.html>**

### Аутентификация через Swagger

1. Откройте Swagger UI
2. Найдите раздел **Authentication** → `POST /api/auth/login`
3. Нажмите **Try it out**, введите учётные данные:

   ```json
   { "login": "admin", "password": "admin123" }
   ```

4. Скопируйте токен из ответа
5. Нажмите кнопку **Authorize** (вверху страницы), введите `Bearer <токен>`
6. Теперь все защищённые эндпоинты доступны для выполнения

### Аутентификация

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/auth/login` | Вход по логину/паролю (JSON). Возвращает JWT-токен и устанавливает HttpOnly Cookie |
| GET | `/api/auth/me` | Информация о текущем пользователе |

**Пример ответа `/api/auth/login`** (200 OK):

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 1,
    "login": "admin",
    "email": "admin@knowledgebase.local",
    "role": "ADMIN"
  }
}
```

### Управление пользователями (только ADMIN)

**Базовый путь:** `/api/admin/users`

| Метод | Путь | Описание | Параметры |
|-------|------|----------|-----------|
| GET | `/api/admin/users` | Список пользователей с пагинацией | `page` (0-based), `size`, `sortBy`, `sortDir` |
| GET | `/api/admin/users/{id}` | Данные конкретного пользователя | |
| POST | `/api/admin/users` | Создание пользователя | Тело: `{ login, email, password, role }` |
| PUT | `/api/admin/users/{id}` | Обновление логина/email/роли | Тело: `{ login, email, role }` |
| DELETE | `/api/admin/users/{id}` | Удаление пользователя | Возвращает 409, если есть связанные данные |
| PUT | `/api/admin/users/{id}/password` | Сброс пароля | Тело: `{ newPassword }` |

> Роль в JWT обновляется при следующем входе пользователя.

### Управление пространствами

**Административные эндпоинты** (ADMIN only):

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/admin/spaces` | Все пространства системы |
| POST | `/api/admin/spaces` | Создание пространства |
| POST | `/api/admin/spaces/{spaceId}/permissions` | Назначение прав пользователю |

**Пользовательские эндпоинты** (все авторизованные):

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/spaces` | Пространства, доступные текущему пользователю. ADMIN видит все |

### Права доступа

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/user/permissions?spaceId={id}` | Права текущего пользователя в пространстве. Возвращает список прав и флаги `canRead`, `canEdit`, `canCreate` для UI |
| GET | `/api/user/spaces` | Все пространства с правами пользователя |

Типы прав: `READ`, `WRITE`, `OWNER`

### Системные эндпоинты

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/actuator/health` | Health check |
| GET | `/swagger-ui.html` | Swagger UI |
| GET | `/api-docs` | OpenAPI спецификация (JSON) |

## 🔐 Роли и права

| Роль | Описание |
|------|----------|
| **ADMIN** | Полный доступ. Управление пользователями, пространствами и правами. Видит все пространства. |
| **EDITOR** | Создание и редактирование документов в пространствах с правом `WRITE`. |
| **READER** | Только чтение в пространствах с правом `READ`. |

ADMIN автоматически получает `[READ, WRITE, OWNER]` во всех пространствах.

## 🛡️ Безопасность

- **JWT в HttpOnly Cookie** — токен недоступен из JavaScript
- **CSRF защита** — Cookie-based токен для AJAX-запросов
- **BCrypt** — хеширование паролей (strength 10)
- Двойная проверка ролей: на уровне SecurityConfig + `@PreAuthorize` на методах контроллеров
