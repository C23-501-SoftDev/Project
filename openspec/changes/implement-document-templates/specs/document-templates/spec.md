## ADDED Requirements

### Requirement: Get Available Templates
The system SHALL provide an API to retrieve all available document templates.

#### Scenario: Successful templates retrieval
- **WHEN** user requests available templates
- **THEN** system returns a list of all templates.

### Requirement: Create Document from Template
The system SHALL allow creation of a new document based on a selected template.

#### Scenario: Successful document creation from template
- **WHEN** user selects a template and confirms creation
- **THEN** system creates a new document initialized with the template content.
