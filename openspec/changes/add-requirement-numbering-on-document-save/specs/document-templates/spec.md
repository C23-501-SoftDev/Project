## MODIFIED Requirements

### Requirement: Auto-generate requirement numbers for template documents
The system SHALL assign unique requirement numbers in format `REQ-XXX` to requirement rows in documents created from templates. The system SHALL assign numbers during initial template document creation and SHALL assign numbers to newly added missing requirement rows when an existing template document is saved.

#### Scenario: First document in a space starts numbering from REQ-001
- **GIVEN** a document is created from a template containing requirement tables in a space without previous requirement counters
- **WHEN** the document is saved
- **THEN** all requirement rows receive sequential numbers starting from `REQ-001`

#### Scenario: Numbering continues within the same space and template
- **GIVEN** a space already has documents created from the same template with requirements numbered up to `REQ-010`
- **WHEN** a new document is created from that template in the same space
- **THEN** requirement rows in the new document continue from `REQ-011`

#### Scenario: Numbering is independent between spaces
- **GIVEN** space A already has requirement numbers assigned from a template
- **WHEN** a document is created from the same template in space B
- **THEN** numbering in space B starts independently from `REQ-001`

#### Scenario: Saving a template document numbers newly added requirement rows
- **GIVEN** an existing document was created from a template
- **AND** the document contains a requirement table with existing numbered rows
- **AND** the user adds a new requirement row with an empty first cell
- **WHEN** the document is saved through `PUT /api/documents/{id}`
- **THEN** the new row receives the next available `REQ-XXX` number for that document's space and template
- **AND** previously assigned `REQ-XXX` values remain unchanged.

#### Scenario: Saving a template document returns actual saved content
- **GIVEN** an existing document was created from a template
- **AND** the user saves content containing a new unnumbered requirement row
- **WHEN** `PUT /api/documents/{id}` succeeds
- **THEN** the response content contains the newly assigned `REQ-XXX` number
- **AND** the response content matches the content persisted for the document.

#### Scenario: Non-template documents are not auto-numbered on save
- **GIVEN** an existing document was not created from a template
- **AND** the document content contains a table whose first header cell is `№`
- **WHEN** the document is saved through `PUT /api/documents/{id}`
- **THEN** the system preserves the submitted content without assigning requirement numbers.
