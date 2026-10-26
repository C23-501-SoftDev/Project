# База знаний

Веб-приложение для Markdown-документов: пространства и права, черновики и публикация, Git-история, вложения, поиск, экспорт HTML/PDF/DOCX/ZIP и локальный RAG с проверяемыми источниками. Spring Boot, Thymeleaf/JavaScript, PostgreSQL/pgvector, JGit и Ollama.

## Запуск одной командой

Нужны Git и работающий Docker Engine с Compose. На macOS можно использовать Colima или Docker Desktop; Java, Maven, PostgreSQL и Ollama на хосте для этого варианта не требуются.

После клонирования и перехода в каталог проекта:

```sh
git clone https://github.com/C23-501-SoftDev/Project.git
cd Project
docker compose up -d --build
```

Откройте [приложение](http://127.0.0.1:8080). Для явного ожидания полной готовности:

```sh
docker compose up -d --build --wait --wait-timeout 1800
```

`.env` не нужен. Первый запуск собирает приложение, создаёт локальные секреты и загружает модели, поэтому занимает больше времени. Для новой базы вход: `admin` / `admin123`; пароль меняется в управлении пользователями. При миграции сохраняются существующие учётные записи.

```sh
docker compose ps -a
docker compose logs -f app
docker compose down
```

Обычный `down` сохраняет статьи, версии, вложения, модели и секреты в named volumes. **`docker compose down -v` удаляет эти данные выбранного проекта** и нужен только для осознанного сброса. PostgreSQL и Ollama не публикуют порты на хост; приложение доступно только через loopback. Для другого порта: `APP_PORT=8081 docker compose up -d --build`.

[Полная Docker-инструкция: установка Colima, готовность, данные, перенос и диагностика](docs/DOCKER_LOCAL_RU.md). На Mac Ollama внутри Linux VM работает на CPU; скорость нативного Metal к этому варианту не относится. [Нативный macOS-стек](docs/LOCAL_SETUP_RU.md) сохранён как альтернативный способ запуска на порту 18081.

Текущая Docker-приёмка: ARM64-стек, подготовка моделей, реальный RAG, перенос актуальных пользовательских данных и сохранность при down/up проверены. Полный `./run-e2e.sh` с Linux Chromium в контейнере прошёл **158/158 тестов за 1,3 минуты**; отдельный прогон Chromium на Mac против Docker backend также прошёл 158/158. Холодная загрузка моделей потребовала одного сетевого retry; [подтверждённые результаты и границы](tests/E2E/E2E_RESULTS.md).

## Документация

- [Архитектура, подробный стек, схемы, выполненные изменения и план](docs/PROJECT_REVIEW_RU.md).
- [Контракты страниц и API](BACKTRACKER.md) — поддерживаемый реестр вместо дублирующих таблиц в README; [Swagger UI запущенного приложения](http://127.0.0.1:8080/swagger-ui/index.html).
- [Локальный RAG: модели, API, права, очередь и ограничения](docs/RAG_LOCAL.md).
- [Экспорт изображений и вложений, автономный HTML/ZIP](docs/EXPORTS.md).
- [Полные команды проверки](openspec/quality-gates.md) и [фактические результаты](tests/E2E/E2E_RESULTS.md).
- [Нагрузочная приёмка 25 пользователей и отдельный stress 50](tests/performance/results/README.md).
- [Реальная оценка RAG и её границы](tests/rag/results/README.md).

API с Cookie-аутентификацией требует CSRF для изменяющих запросов: сначала получите `XSRF-TOKEN` через `GET /login`, затем передавайте его в `X-XSRF-TOKEN` вместе с cookies. Сам интерфейс делает это автоматически. В Docker-профиле внешние AI-переписывание и SMTP выключены; RAG использует только локальный Ollama.

## Работа с проектом

```text
backend/              Java, Spring Boot, Thymeleaf, static JS/CSS и тесты
docs/                 Инструкции запуска и обзор проекта
openspec/             Требования, feature registry, changes и quality gates
scripts/              Сценарии окружения и разработки
tests/E2E/            Playwright и браузерные проверки
tests/rag/            Датасеты, модельная оценка и исходные результаты
tests/performance/    Сценарии и измерения нагрузки
```

Исходники редактируются в checkout на хосте. Рабочий контейнер содержит собранный JAR, а не Maven-окружение с проброшенными SSH-ключами. После изменения кода повторите `docker compose up -d --build`.

Перед изменением изучите [feature registry](openspec/feature-registry.json), [контекст OpenSpec](openspec/config.yaml) и соответствующий change/spec. Для задач с изменением API обновляйте BACKTRACKER. OpenSpec workflow: proposal → design/spec/tasks → implementation → [quality gates](openspec/quality-gates.md) → синхронизация specs и archive. Соседний репозиторий [Docs](https://github.com/C23-501-SoftDev/Docs) содержит исходные требования и backlog; локальная ожидаемая структура — `Project/` и `Docs/` рядом.

Тесты, создающие или удаляющие данные, запускайте на отдельном Compose-проекте/тестовой базе. Инструкции и helper-команды для этого приведены в [Docker-руководстве](docs/DOCKER_LOCAL_RU.md) и [quality gates](openspec/quality-gates.md); пользовательские volumes не являются тестовыми fixtures.
