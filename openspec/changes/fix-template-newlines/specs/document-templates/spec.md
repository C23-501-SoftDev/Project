## MODIFIED Requirements

### Requirement: Get Available Templates
The system SHALL provide an API to retrieve all available document templates. Template Markdown content MUST preserve structural line breaks exactly enough for Markdown block parsing, including headings, tables, lists, code fences, and comments.

#### Scenario: Successful templates retrieval
- **WHEN** user requests available templates
- **THEN** system returns a list of all templates with their metadata.

#### Scenario: Template content preserves Markdown line breaks
- **GIVEN** a system template contains Markdown headings and tables
- **WHEN** user requests available templates
- **THEN** the returned template content contains real newline characters between Markdown blocks
- **AND** headings such as `## Обзор системы` are not joined into the preceding paragraph.

### Requirement: Create Document from Template
The system SHALL allow creation of a new document based on a selected template. Documents created from templates MUST persist the template Markdown with structural line breaks preserved.

#### Scenario: Successful document creation from template
- **WHEN** user selects a template and confirms creation
- **THEN** system creates a new document initialized with the template content.

#### Scenario: Created document renders template Markdown structure
- **GIVEN** a selected template contains Markdown headings and tables
- **WHEN** a document is created from that template
- **THEN** the saved document content contains real newline characters between Markdown blocks
- **AND** the document view renders headings and tables as Markdown structure rather than one flattened paragraph.

### Requirement: Auto-generate requirement numbers for template documents
The system SHALL assign unique requirement numbers in format `REQ-XXX` to every requirement row in documents created from templates.

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
