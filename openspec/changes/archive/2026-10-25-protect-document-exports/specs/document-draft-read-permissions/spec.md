## ADDED Requirements

### Requirement: Alternate representations obey document visibility
The system MUST require space read access and document visibility before exporting HTML/PDF/DOCX, listing/comparing versions, or listing/downloading attachments.

#### Scenario: Another reader requests a draft export
- **WHEN** a user with space READ access requests another author's draft in any export format
- **THEN** the request returns 403 before document conversion.

#### Scenario: Authorized draft and published access
- **WHEN** the author or an administrator with space access requests a draft, or a reader requests a published document
- **THEN** the requested representation is returned.

#### Scenario: Alternative read routes cannot bypass the draft rule
- **WHEN** another reader requests a draft's versions, diff, attachment list or attachment download
- **THEN** the request returns 403.
