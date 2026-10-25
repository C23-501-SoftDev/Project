# git-commit-on-document-save Specification

## Purpose
Git snapshots and database version metadata for changes to published documents, including failure handling and version provenance.

## Requirements
### Requirement: Version changed published documents
Saving a changed published document SHALL create one Git snapshot and one version row containing the matching SHA, path, editor, comment and commit time.
#### Scenario: Content or metadata changes
- **WHEN** an authorized editor changes content or metadata of a published document
- **THEN** the version row references the commit produced by that save and the commit identifies the editor
#### Scenario: Unchanged save
- **WHEN** a save changes neither content nor metadata
- **THEN** no empty published version is created

### Requirement: Report storage failures accurately
The system MUST reject failed saves and SHALL log the orphan SHA if persistence fails after a successful Git commit.
#### Scenario: Git failure
- **WHEN** the Git snapshot cannot be committed
- **THEN** no successful version response or version row is produced
#### Scenario: Database failure after commit
- **WHEN** the version row cannot be persisted after Git succeeds
- **THEN** the database transaction rolls back, the request fails and the orphan SHA is recorded for recovery
