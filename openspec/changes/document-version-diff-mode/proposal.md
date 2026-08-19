## Why

История версий уже имеет маршрут страницы и хранит SHA Git-коммитов, но пользователь не может увидеть, чем отличаются две ревизии. Это не позволяет быстро проверить конкретную правку и делает сохранённые версии малоценными для редактора и аудитора.

FeatureId: `document-version-diff-mode` (новая запись в `openspec/feature-registry.json`).

## What Changes

- Добавить API сравнения двух SHA одной истории документа: `GET /api/documents/{id}/diff?from={hash1}&to={hash2}`.
- Получать diff на сервере через JGit только для Git-файлов выбранного документа и преобразовывать его в безопасную структурированную модель строк.
- Реализовать экран истории с выбором пары версий и HTML-представлением в стиле GitHub: удалённые строки выделяются красным, добавленные — зелёным, неизменённый контекст остаётся нейтральным.
- Проверять право чтения документа и принадлежность обоих SHA этому документу; не передавать клиенту произвольный Git path или неэкранированный HTML из документа.

## Capabilities

### New Capabilities

- `document-version-diff-mode`: выбор любых двух сохранённых версий доступного документа и визуальное построчное сравнение их состояний.

### Modified Capabilities

- *(none)*

## Impact

- **Backend:** `DocumentVersionRepository`, новый use case/service, JGit adapter, REST controller и DTO diff-ответа.
- **Frontend:** существующий Thymeleaf-шаблон `pages/document-history.html`, его JavaScript и стили diff-представления.
- **API:** новый read-only endpoint; требуется актуализация `BACKTRACKER.md`.
- **Dependencies:** сохранение Git-коммитов и метаданных версий (`git-commit-on-document-save`), последующая реализация списка версий для выбора SHA.

## References

- `../../Docs/documents/backlog-descriptions/E2 Жизненный цикл и Версионность (Lifecycle & Versioning)/F2.2 Контроль версий (Git Integration)/US2.2.3 Режим сравнения версий (Diff)/description.md`
- `../../Docs/documents/backlog.json` — US2.2.3 и US2.2.6.
