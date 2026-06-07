## ADDED Requirements

### Requirement: Admin Spaces Table Sortable Headers
Система SHALL предоставлять страницу `/admin/spaces`, на которой заголовки таблицы пространств, кроме `Actions`, являются кликабельными и управляют сортировкой по соответствующему столбцу.

#### Scenario: Open admin spaces page with default natural ordering
- **WHEN** authenticated administrator opens `/admin/spaces`
- **THEN** page requests `/api/admin/spaces?page=0&size=20&sortBy=createdAt&sortDir=asc` and renders spaces in natural order

#### Scenario: Click on column header toggles sorting
- **WHEN** administrator clicks the `Name` header twice
- **THEN** the first click requests `/api/admin/spaces?...&sortBy=name&sortDir=asc`, the second click requests `/api/admin/spaces?...&sortBy=name&sortDir=desc`

#### Scenario: Status column is sortable
- **WHEN** administrator clicks the `Status` header
- **THEN** the table sorts by the status column and updates the request parameters accordingly

### Requirement: Admin Spaces API Supports Sorting
Система SHALL поддерживать сортировку списка пространств через `GET /api/admin/spaces` с параметрами `sortBy` и `sortDir`.

#### Scenario: Sort by owner
- **WHEN** administrator requests `/api/admin/spaces?page=0&size=20&sortBy=ownerLogin&sortDir=asc`
- **THEN** system returns the first page sorted by owner login ascending

#### Scenario: Invalid sort field falls back to default
- **WHEN** administrator requests `/api/admin/spaces?page=0&size=20&sortBy=unknown&sortDir=asc`
- **THEN** system uses the default sort field and returns a valid response