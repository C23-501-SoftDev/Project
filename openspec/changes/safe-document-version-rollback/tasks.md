## 1. Domain and Git access

- [x] 1.1 Добавить в `DocumentContentRepository` контракт чтения содержимого файла из конкретной Git-версии с валидацией пути и SHA.
- [x] 1.2 Реализовать чтение blob из дерева выбранного commit в `JGitDocumentContentRepository`, не изменяя `HEAD`, и переиспользовать синхронизацию локального репозитория.
- [x] 1.3 Добавить domain-ошибки/результат восстановления, необходимые для однозначного отображения invalid SHA, незарегистрированной версии и Git-ошибки.

## 2. Application workflow and persistence indexing

- [x] 2.1 Реализовать в `DocumentVersionService` или отдельном use case проверку формата SHA, наличия документа и принадлежности версии указанному `documentId` до обращения к Git.
- [x] 2.2 Реализовать workflow Git → DB: прочитать выбранный текст, создать новый snapshot с metadata sidecar текущего документа, сохранить/переиндексировать документ и новую `DocumentVersion` с SHA и редактором.
- [x] 2.3 Обработать Git-ошибку без DB-изменений и DB-ошибку после Git-коммита с rollback DB и журналированием orphan SHA; не применять Git reset/checkout.
- [x] 2.4 Добавить audit record и событие после подтверждения DB-транзакции для успешного восстановления.

## 3. REST API and history UI

- [x] 3.1 Добавить `POST /api/documents/{id}/versions/{gitHash}/restore`, `@PreAuthorize` с правом EDIT, CSRF-совместимую обработку и OpenAPI-описание.
- [x] 3.2 Добавить response DTO и exception mapping: 201 для успеха, 400 для malformed SHA, 403 без EDIT и 404 для не принадлежащей документу версии.
- [x] 3.3 Обновить страницу истории и JavaScript: доступная только редактору кнопка восстановления, явное подтверждение, CSRF-запрос, сообщение результата и перезагрузка списка версий после успеха.
- [x] 3.4 Обновить `BACKTRACKER.md` — актуализировать затронутые эндпоинты и их статусы.

## 4. Tests and verification

- [x] 4.1 Unit: корректный rollback читает зарегистрированную версию, создаёт новый SHA и DB-версию с текущим редактором, не изменяя исходную историю.
- [x] 4.2 Unit: malformed/чужой SHA и отсутствие EDIT не запускают чтение Git и не создают новую версию.
- [x] 4.3 Unit: Git-ошибка не меняет БД; ошибка persistence откатывает БД и журналирует orphan SHA без Git reset.
- [x] 4.4 Integration: временный JGit repo подтверждает, что новый commit содержит текст выбранной версии, новый metadata sidecar и не перемещает HEAD на старый commit.
- [x] 4.5 Integration/UI: editor получает 201 и обновлённую историю; читатель получает 403; отмена confirmation не отправляет POST.
- [x] 4.6 Проверка критериев качества — выполнить все шаги из `openspec/quality-gates.md`: `mvn clean compile`, `mvn test` и, при доступной PostgreSQL, smoke-check `/actuator/health`.
