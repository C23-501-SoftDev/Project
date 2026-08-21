# Spec: Окончательное удаление документов (Hard-delete)

## ADDED Requirements

### Requirement: Hard-delete documents
The system SHALL allow hard-delete operation only for users with ADMIN role.
The system SHALL allow hard-delete only for documents with DELETED status.
The system SHALL permanently remove the document from the system.

#### Scenario: Administrator permanently deletes a document
- GIVEN a document has DELETED status
- AND the user has ADMIN role
- WHEN the administrator performs hard-delete
- THEN the document is permanently removed from the database
- AND related data is removed
### Requirement: Hard-delete document hierarchy
The system SHALL preserve document tree integrity during hard-delete.
The system SHALL move direct children of the deleted document to its parent before physical removal.
The system SHALL NOT delete child documents automatically.

#### Scenario: Hard-delete document with children
- GIVEN document B has status DELETED
- AND document B has child document C
- WHEN administrator permanently deletes document B
- THEN document C remains in the system
- AND document C is assigned to the parent of document B
### Requirement: Hard-delete from recycle bin
The system SHALL allow hard-delete operation only from the administrative recycle bin.
The system SHALL permanently remove a document after hard-delete and the document MUST NOT be recoverable.

#### Scenario: Administrator deletes document from recycle bin
- GIVEN a document is displayed in the administrative recycle bin
- AND the user has ADMIN role
- WHEN administrator selects "Delete permanently"
- THEN the document is removed permanently
- AND the document cannot be restored

