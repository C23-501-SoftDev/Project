## Why

В текущей реализации боковая панель отображает только плоский список пространств, что затрудняет навигацию по большим объемам данных. Необходимо реализовать иерархическое дерево пространств и документов для улучшения UX и упрощения доступа к контенту.

## What Changes

- Добавление иерархического отображения документов в боковой панели внутри пространств.
- Обновление `PageController` для передачи структуры документов в модель.
- Добавление сервисного метода для формирования дерева документов.
- Изменение шаблона Thymeleaf `layout.html` для рендеринга рекурсивного дерева документов.

## Capabilities

### New Capabilities
- `sidebar-hierarchy`: Реализация иерархической структуры документов в боковой панели.

### Modified Capabilities
- 

## Impact

- `backend/src/main/java/com/knowledgebase/interfaces/rest/controller/PageController.java`: обновление данных для модели.
- `backend/src/main/resources/templates/layout.html`: изменение структуры вывода в UI.
- `backend/src/main/java/com/knowledgebase/application/service/DocumentService.java`: добавление метода для построения дерева.
