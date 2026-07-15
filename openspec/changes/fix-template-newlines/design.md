## Context

Содержимое системных шаблонов хранится в таблице `templates.content` как Markdown. Текущий seed использует XML-атрибут `value` в Liquibase, например:

```xml
<column name="content" value="# Описание архитектуры

## Обзор системы
..."/>
```

Для XML-атрибутов переводы строк нормализуются, поэтому БД получает строку без Markdown-разделителей. UI и backend затем работают с уже повреждённым контентом.

## Goals / Non-Goals

**Goals:**
- Гарантировать, что системные шаблоны в БД содержат реальные `\n`.
- Исправить уже существующие записи системных шаблонов.
- Сохранить корректную работу автонумерации требований `REQ-XXX` для таблиц требований.
- Покрыть сценарий тестом, который ловит регрессию схлопывания Markdown в одну строку.

**Non-Goals:**
- Не менять модель `Template`, DTO или публичный контракт `GET /api/templates`.
- Не добавлять UI редактирования шаблонов.
- Не менять формат автонумерации требований.

## Decisions

- **Liquibase seed:** не менять уже применённый changeset `005-insert-system-templates`, потому что это меняет checksum и блокирует старт существующих БД.
- **Existing and fresh data fix:** добавить новый changeset после текущих миграций, который обновляет `content` системных шаблонов из `.md` файлов. Для существующих БД он исправляет уже вставленные записи; для fresh DB он выполняется сразу после `005` и приводит данные к корректному Markdown.
- **Resource naming:** хранить файлы шаблонов в стабильной директории ресурсов Liquibase, например `backend/src/main/resources/db/changelog/templates/`.
- **Runtime flow:** не менять `TemplateController`, `DocumentService.createDocument` и UI flow, если после исправления данных они получают корректный Markdown.
- **POST response follow-up:** проверить баг, при котором `POST /api/documents` возвращает response из `request.content`, а не из фактически сохранённого template content. Если тест показывает пользовательский дефект сразу после создания, исправить mapper-вызов в рамках этой задачи без изменения API.

## Implementation Notes

- Участок кода для изменений:
  - DB/Liquibase: `backend/src/main/resources/db/changelog/changes/005-insert-system-templates.xml`, новый changeset в `backend/src/main/resources/db/changelog/changes/`.
  - Resources: Markdown-файлы системных шаблонов.
  - Service/controller only if needed: `DocumentController.createDocument` для возврата фактического сохранённого content.
  - Tests: интеграционные тесты шаблонов/создания документа.
- Тест должен проверять минимум:
  - `templates.content` для `Описание архитектуры` содержит `\n## Обзор системы`.
  - созданный документ из шаблона содержит Markdown с переносами строк, а не одну строку.
  - шаблон `Требования к системе` всё ещё получает номера `REQ-001` в строках таблиц требований.

## Risks / Trade-offs

- [Risk] Повторное обновление системных шаблонов может перезаписать ручные правки в БД. Митигация: обновлять только `is_system = true` и известные системные имена.
- [Risk] Пути `valueClobFile` чувствительны к `relativeToChangelogFile`. Митигация: зафиксировать относительные пути и покрыть миграцию тестом.
- [Risk] Дублирование Markdown между seed и fix changeset. Митигация: использовать одни и те же `.md` файлы для insert и update.
