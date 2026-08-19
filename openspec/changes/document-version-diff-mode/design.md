## Context

`versions` связывает документ с SHA Git-коммита, но его репозиторий пока поддерживает только сохранение. Страница `/documents/{id}/history` уже существует как Thymeleaf placeholder, а `BACKTRACKER.md` резервирует diff endpoint. Git-снимок документа включает Markdown и служебный metadata sidecar; сравнение должно показывать только текст выбранного документа, а не изменения других файлов коммита.

## Goals / Non-Goals

**Goals:**

- Сравнить любые две сохранённые версии одного документа в указанном направлении `from` → `to`.
- Вернуть последовательность строк, пригодную для однозначной и безопасной HTML-отрисовки.
- Разрешить просмотр только пользователю с `READ`-доступом к документу.
- Показать в истории две версии и результаты сравнения без перезагрузки страницы.

**Non-Goals:**

- Создание версий, список версий как самостоятельная фича, восстановление ревизии, сравнение разных документов, merge или patch-экспорт.
- Выполнение shell-команд Git: используется JGit, уже принятый в проекте.
- Символьный/пословный diff и рендер Markdown в diff-строках в первом релизе.

## Decisions

### 1. Контракт API и модель результата

`GET /api/documents/{id}/diff?from=<40-hex SHA>&to=<40-hex SHA>` возвращает `DocumentDiffResponse`: идентификатор документа, SHA `from`/`to` и упорядоченный список `DiffLineResponse`. Каждая строка содержит тип (`CONTEXT`, `ADDED`, `REMOVED`), номер строки до и/или после, и обычный текст строки. Контент сериализуется JSON; frontend вставляет его через `textContent`, поэтому Markdown/HTML пользователя не исполняется.

Отсутствующий параметр, некорректный SHA или совпадающие SHA дают `400`; отсутствующий документ/версия — `404`; SHA, не относящийся к указанному документу, также не раскрывается и даёт `404`; нет `READ`-права — `403`.

### 2. Валидация версии и генерация diff

Расширить `DocumentVersionRepository` методом поиска SHA в разрезе `documentId`. Application service сначала загружает документ, проверяет право доступа на REST boundary и убеждается, что обе SHA зарегистрированы в `versions` для этого документа. В Git port добавить операцию `diffDocumentVersion(path, fromHash, toHash)`.

JGit adapter открывает локальный repository, читает blob объекта `path` из обоих `RevCommit` и выполняет `DiffFormatter`/`RawTextComparator` для одного пути. Если файл отсутствует в одной версии, это трактуется как пустое состояние, так что добавление или удаление документа корректно отображается. Adapter парсит unified diff в typed lines и не включает служебный metadata sidecar, имена других файлов, commit message либо stderr.

### 3. UI истории и отображение

Расширить `pages/document-history.html`: список версий (когда API истории будет доступен) даёт выбрать `before` и `after`, причём по умолчанию для выбранной ревизии берётся предшествующая. Кнопка «Сравнить» доступна только при двух разных SHA. После запроса UI показывает метаданные пары и таблицу с двумя колонками номеров строк и колонкой текста; `REMOVED` получает красный фон и `−`, `ADDED` — зелёный и `+`, `CONTEXT` — нейтральный вид. Пустой diff показывает ясное состояние «Версии не отличаются».

Чтобы эта change оставалась реализуемой независимо от готовности UI списка версий, JS выделяется в небольшой модуль с методом загрузки diff по явно переданным SHA; интеграция с endpoint списка версий оформляется задачей и должна использовать его после появления API истории.

### 4. Слои и обработка ошибок

| Layer | Responsibility |
| --- | --- |
| domain | `DiffLine`, `DocumentDiff`, расширенный version port и Git diff port без Spring-зависимостей. |
| application | Проверка того, что SHA принадлежат документу; orchestration и преобразование доменной модели в данные use case. |
| infrastructure | Spring Data query по `document_id`/`git_hash`; JGit чтение commit tree и diff одного пути. |
| interfaces | `DocumentVersionController` или выделенный version controller, DTO/mapper, `@PreAuthorize`, OpenAPI и history-page JS/CSS. |

Ошибки JGit (нечитаемый repo, отсутствующий commit/tree) журналируются с document id и hash без содержимого документа и переводятся в согласованную серверную ошибку. Размер результата ограничивается конфигурационным максимальным числом diff-строк; превышение возвращает понятный `422`, чтобы один большой документ не создавал неограниченный ответ.

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| SHA внешнего коммита раскрывает чужой контент | До JGit читать SHA только через `versions` данного документа. |
| Коммит меняет несколько файлов | Diff строится строго по `document.gitFilePath`. |
| XSS из Markdown | JSON + DOM `textContent`, без `innerHTML`. |
| Большой diff нагружает сервер/UI | Лимит строк и явная ошибка с предложением выбрать более близкие версии. |
| История версий ещё не реализована | Контракт diff и JS-модуль независимы; выбор подключается к history endpoint в его change. |

## Proposed Code Changes

- **Domain:** `DocumentDiff`, `DiffLine`, `DiffLineType`, методы чтения версий и diff-порт.
- **Application:** `DocumentVersionService`/выделенный use case для валидации пары и получения diff.
- **Infrastructure:** query `findByDocumentIdAndGitHash`, JGit-реализация чтения tree и `DiffFormatter`.
- **Interfaces:** DTO, REST controller, mapper, обработка валидации параметров; `document-history.html`, JS и CSS.
- **Documentation:** `BACKTRACKER.md`, OpenAPI и feature registry.
