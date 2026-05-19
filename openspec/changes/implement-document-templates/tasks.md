## 1. Репозиторий и сервис шаблонов

- [x] 1.1 Создать `Template` сущность в `domain.model` (соответствующую таблице `templates`)
- [x] 1.2 Создать `TemplateRepository` (Spring Data JPA)
- [x] 1.3 Создать `TemplateService` для получения списка шаблонов
- [x] 1.4 Реализовать эндпоинт `GET /api/templates` в `DocumentController` или новом `TemplateController`

## 2. Обновление создания документа

- [x] 2.1 Обновить `DocumentService.createDocument` для принятия `templateId`
- [x] 2.2 Реализовать логику чтения контента шаблона и инициализации документа
- [x] 2.3 Добавить эндпоинты в `BACKTRACKER.md`
- [x] 3.1 Выполнить unit и интеграционные тесты
- [x] 3.2 Проверить работу через Swagger UI
