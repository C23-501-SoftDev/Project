# Project Documentation

## Table of Contents
1. [First-Time Setup](#first-time-setup)
2. [Project Overview](#project-overview)
3. [API Documentation](#api-documentation)

---

# First-Time Setup

## Prerequisites

- **Docker Desktop** installed and running
  - [macOS/Linux](https://docs.docker.com/desktop/)
  - [Windows](https://docs.docker.com/desktop/install/windows-install/)
- **Git** installed (for cloning and management)

---

## Unix/Linux/macOS Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/C23-501-SoftDev/Project.git
cd Project
```

### Step 2: Create Environment File

Copy the example environment file and customize if needed:

```bash
cp .env.example .env
```

The `.env` file is pre-configured with:
- `GIT_REPO_URL=git@github.com:C23-501-SoftDev/Project.git` (auto-cloning enabled)
- `GIT_REPO_SSH_URL=git@github.com:C23-501-SoftDev/Project.git` (**for git push/pull inside container**)
- `POSTGRES_DB=knowledge_base`
- `POSTGRES_USER=kb_user`
- `POSTGRES_PASSWORD=strong_password`

**⚠️ Important:** Ensure SSH is configured for git operations inside the container:
1. Check that `GIT_REPO_SSH_URL` in `.env` matches your Git repository SSH address
2. Your SSH key must be in `~/.ssh/` on the host machine
3. Your git config must be set: `git config --global user.name "Your Name"` and `git config --global user.email "your@email.com"`

**Optional:** Edit `.env` to change any values (ports, credentials, git URL, etc.):

```bash
nano .env
# or
vim .env
```

### Step 3: Start Services

Build and start all containers:

```bash
make dev-up
# or
docker compose --env-file .env up -d --build
```

Wait 30-40 seconds for the application to initialize. Check logs:

```bash
docker logs kb_app -f
```

You should see:
```
[startup.sh] Переключаем git remote на SSH: git@github.com:C23-501-SoftDev/Project.git
[startup.sh] Git remote успешно обновлен
```

This means the **startup script automatically configured git SSH remote** for you.

Press `Ctrl+C` to exit logs.

### Step 4: Verify

Check container status:

```bash
docker ps
```

You should see:
- `kb_app` (project application) - Status: `Up` with `health: healthy`
- `kb_postgres` (PostgreSQL database) - Status: `Up (healthy)`

Access the application:
- **Application**: http://localhost:8080
- **PostgreSQL**: localhost:5432

### Step 5: Stop Services

```bash
make dev-down
# or
docker compose down
```

To stop and remove all data (volumes):

```bash
docker compose down -v
```

---

## Working with Git inside the Container

The **startup script** automatically:
1. ✅ Configures git SSH remote from `GIT_REPO_SSH_URL` environment variable
2. ✅ Mounts your git config (`~/.gitconfig`) so commits use your name/email
3. ✅ Mounts your SSH keys (`~/.ssh`) for push/pull operations

### Enter the Container

```bash
make docker-shell
# or
docker exec -it kb_app /bin/sh
```

### Verify Git is Configured

```bash
# Check SSH remote
git remote -v

# Check your git config
git config --global user.name
git config --global user.email
```

Expected output:
```
origin  git@github.com:C23-501-SoftDev/Project.git (fetch)
origin  git@github.com:C23-501-SoftDev/Project.git (push)
```

### Make Commits and Push

```bash
git add .
git commit -m "Описание вашего изменения"
git push origin HEAD
```

✅ **No password required** — SSH keys and config are already set up!

---

## Windows Setup

### Step 1: Clone the Repository

Open **Command Prompt** or **PowerShell** and run:

```powershell
git clone https://github.com/C23-501-SoftDev/Project.git
cd Project
```

Or use **Git Bash** (if installed):

```bash
git clone https://github.com/C23-501-SoftDev/Project.git
cd Project
```

### Step 2: Create Environment File

**Option A: Using Command Prompt**

```cmd
copy .env.example .env
```

**Option B: Using PowerShell**

```powershell
Copy-Item -Path .env.example -Destination .env
```

**Option C: Using Git Bash**

```bash
cp .env.example .env
```

The `.env` file is pre-configured with:
- `GIT_REPO_URL=git@github.com:C23-501-SoftDev/Project.git` (auto-cloning enabled)
- `GIT_REPO_SSH_URL=git@github.com:C23-501-SoftDev/Project.git` (**for git push/pull inside container**)
- `POSTGRES_DB=knowledge_base`
- `POSTGRES_USER=kb_user`
- `POSTGRES_PASSWORD=strong_password`

**⚠️ Important:** Ensure SSH is configured for git operations inside the container:
1. Check that `GIT_REPO_SSH_URL` in `.env` matches your Git repository SSH address
2. Your SSH key must be in `~/.ssh/` on your Windows user directory
3. Your git config must be set: `git config --global user.name "Your Name"` and `git config --global user.email "your@email.com"`

**Optional:** Edit `.env` to change any values:

```powershell
# Open in Notepad
notepad .env

# Or use your preferred editor
code .env  # Visual Studio Code
```

### Step 3: Start Services

Open **PowerShell** (or Command Prompt) in the project directory and run:

```powershell
docker compose --env-file .env up -d --build
```

Wait 30-40 seconds for the application to initialize. Check logs:

```powershell
docker logs kb_app -f
```

You should see:
```
[startup.sh] Переключаем git remote на SSH: git@github.com:C23-501-SoftDev/Project.git
[startup.sh] Git remote успешно обновлен
```

This means the **startup script automatically configured git SSH remote** for you.

Press `Ctrl+C` to exit logs.

### Step 4: Verify

Check container status:

```powershell
docker ps
```

You should see:
- `kb_app` (project application) - Status: `Up` with `health: healthy`
- `kb_postgres` (PostgreSQL database) - Status: `Up (healthy)`

Access the application:
- **Application**: http://localhost:8080
- **PostgreSQL**: localhost:5432

### Step 5: Stop Services

```powershell
docker compose down
```

To stop and remove all data (volumes):

```powershell
docker compose down -v
```

---

## Troubleshooting

### Container Won't Start

Check the application logs:

```bash
# Unix/macOS/Windows Git Bash
docker logs kb_app

# Windows PowerShell
docker logs kb_app
```

### Port Already in Use

If ports 8080 or 5432 are in use, edit `.env`:

```env
APP_PORT=8081
POSTGRES_PORT=5433
```

Then restart:

```bash
docker compose down
docker compose up --pull always -d
```

### Permission Denied (Unix/Linux)

If you get permission errors on Linux/macOS, run Docker commands with `sudo` or add your user to the docker group:

```bash
sudo usermod -aG docker $USER
newgrp docker
```

### Database Connection Issues

Verify PostgreSQL is healthy:

```bash
docker ps
```

If `kb_postgres` status shows unhealthy, restart:

```bash
docker compose restart postgres
```

### SSH Auth Warning (Unix/macOS)

The warning `SSH_AUTH_SOCK variable is not set` is safe to ignore. It appears because the repository is cloned via HTTPS internally, not SSH.

### Git SSH Not Working in Container

If you can't push/pull from inside the container, check:

1. **SSH keys are accessible:**
   ```bash
   docker exec kb_app ls -la /home/app/.ssh/
   # Should show: id_rsa, id_rsa.pub, known_hosts, config (or similar)
   ```

2. **Git SSH remote is configured:**
   ```bash
   docker exec kb_app git remote -v
   # Should show: git@github.com:...
   ```

3. **Git config is loaded:**
   ```bash
   docker exec kb_app git config --global user.name
   # Should show your name
   ```

4. **SSH key permissions are correct:**
   ```bash
   docker exec kb_app ls -l /home/app/.ssh/id_rsa
   # Should show: -rw------- (600 permissions)
   ```

**Solution:** If SSH keys are missing or have wrong permissions:
- On your **host machine**, ensure keys are in `~/.ssh/` with correct permissions:
  ```bash
  chmod 700 ~/.ssh
  chmod 600 ~/.ssh/id_rsa
  chmod 644 ~/.ssh/id_rsa.pub
  ```
- Then restart containers: `docker compose down && docker compose up -d --build`

---

# Project Overview

## Directory Structure

```
Project/
├── backend/           # Spring Boot application (Java/Maven)
│   └── Dockerfile     # App container with startup script (auto-configures git SSH)
├── frontend/          # Frontend code
├── docker-compose.yml # Multi-container orchestration (mounts SSH keys & git config)
├── .env              # Environment variables (created locally, includes GIT_REPO_SSH_URL)
├── .env.example      # Example environment template
├── README.md         # This file
└── BACKTRACKER.md    # API specification and page/endpoint mapping
```

### What Happens on `docker compose up`

1. **Dockerfile builds** with embedded startup script
2. **Container starts** and runs startup script:
   - If `GIT_REPO_SSH_URL` is set → switches git remote to SSH
   - Loads your git config (user.name, user.email)
   - Mounts your SSH keys for authentication
3. **Container waits** for your commands (`docker exec` or interactive shell)

---

# API Documentation

> **Цель:** Документ для синхронизации фронтенд- и бэкенд-разработки. Для каждой страницы указаны все API-запросы, которые она использует или должна использовать.
>
> **Легенда статусов:**
> - ✅ — API реализован
> - ❌ — API НЕ реализован (требуется разработка)
> - 🔶 — Частично реализован

> ⚠️ **Правило поддержки:**
> Если разработчик добавляет, изменяет или удаляет API-эндпоинты, то он обязан обновить файл `BACKTRACKER.md`: изменить статусы (❌→✅), добавить/убрать строки.

## Аутентификация

### Страница входа (`GET /login`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| POST | `/login` | HTML-форма входа → установка JWT Cookie + CSRF Cookie, редирект на `/` | ✅ |
| POST | `/api/auth/login` | REST-вход (JSON) → `{ token, user }` + HttpOnly Cookie | ✅ |

**После входа:** редирект на `/` (главная страница)

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

---

## Страница истории версий (`GET /documents/{id}/history`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/{id}/versions?page=0&size=20` | Список версий с пагинацией (gitHash, author, comment, createdAt) | ❌ |
| GET | `/api/documents/{id}/versions/{gitHash}` | Содержимое конкретной версии | ❌ |
| GET | `/api/documents/{id}/diff?from={hash1}&to={hash2}` | Сравнение двух версий (diff) | ❌ |
| POST | `/api/documents/{id}/restore/{gitHash}` | Откат к версии (создаёт новую версию-копию) | ❌ |

---

## Страница поиска (`GET /search?q=...`)

| Метод | Эндпоинт | Описание | Статус |
|-------|----------|----------|--------|
| GET | `/api/documents/search?q=запрос&page=0&size=20` | Поиск по названию | ❌ |
| GET | `/api/documents/search?q=запрос&spaceId={id}` | Поиск в конкретном пространстве | ❌ |
| GET | `/api/documents/search?q=запрос&dateFrom=...&dateTo=...` | Поиск + фильтрация по дате | ❌ |
| GET | `/api/documents/search?q=запрос&status=Published` | Поиск + фильтрация по статусу | ❌ |

---

## Экспорт

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
| GET | `/api/admin/users?page=0&size=20&sortBy=login&sortDir=asc` | Список пользователей с пагинацией и сортировкой | ✅ |
| GET | `/api/admin/users?role=EDITOR` | Фильтрация по роли | ✅ |
| GET | `/api/admin/users?search=admin` | Поиск по логину/email | ✅ |
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
| POST | `/api/admin/spaces` | Создание пространства (`{ name, description, ownerId }`) | ✅ |
| PUT | `/api/admin/spaces/{id}` | Обновление пространства | ❌ |
| DELETE | `/api/admin/spaces/{id}` | Удаление пространства (RESTRICT если есть документы) | ❌ |
| POST | `/api/admin/spaces/{spaceId}/permissions` | Назначение прав (`{ userId, permissionType: READ\|WRITE\|OWNER }`) | ✅ |
| GET | `/api/admin/spaces/{id}/permissions` | Список прав пространства (с полями userLogin, userEmail) | ✅ |

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
