## ADDED Requirements

### Requirement: Document Hierarchy Rules
Система ДОЛЖНА предотвращать создание циклических зависимостей в иерархии документов и обеспечивать целостность Пространства (Space).

#### Scenario: Prevent self-parenting
- **WHEN** пользователь пытается установить документ в качестве собственного родителя при создании или обновлении
- **THEN** система возвращает ошибку 422 Unprocessable Entity

#### Scenario: Prevent circular dependency
- **WHEN** пользователь пытается установить в качестве родителя один из дочерних (вложенных) документов текущего документа
- **THEN** система возвращает ошибку 422 Unprocessable Entity с описанием нарушения иерархии

#### Scenario: Space isolation
- **WHEN** пользователь пытается установить в качестве родителя документ из другого Пространства (Space)
- **THEN** система отклоняет запрос с ошибкой 422 Unprocessable Entity

### Requirement: Title Uniqueness within Hierarchy Level
Система ДОЛЖНА гарантировать уникальность заголовка документа среди всех документов, имеющих того же родителя в рамках одного Пространства.

#### Scenario: Conflict on the same level
- **WHEN** пользователь сохраняет документ с заголовком, который уже существует у другого документа с тем же `parentId` в текущем `spaceId`
- **THEN** система возвращает ошибку 422 Unprocessable Entity с указанием на конфликт заголовка

### Requirement: Attachment Reference Validation
Система ДОЛЖНА проверять существование всех вложений, на которые ссылается документ.

#### Scenario: Missing attachment reference
- **WHEN** документ содержит ссылки на Attachment ID, которых нет в системе
- **THEN** система возвращает ошибку 422 Unprocessable Entity при сохранении
