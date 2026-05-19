## Context

Использование уже существующей таблицы `templates` (миграция 004).
Реализация нового API и обновление сервисного слоя.

## Goals / Non-Goals

**Goals:**
- Реализация `TemplateRepository` (Spring Data JPA).
- Реализация `TemplateService` для доступа к шаблонам.
- Обновление `DocumentService.createDocument` для поддержки инициализации контента из шаблона.
- REST эндпоинт `GET /api/templates`.

**Non-Goals:**
- Сложная система управления правами на сами шаблоны.
- Редактирование шаблонов через UI.

## Decisions

- **API**: `GET /api/templates`.
- **Logic**: При создании документа (POST `/api/documents`) принимать `templateId`. Если передан, сервис читает контент шаблона и инициализирует новый документ этим контентом.
