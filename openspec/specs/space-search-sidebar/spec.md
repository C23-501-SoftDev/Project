# space-search-sidebar Specification

## Purpose
Authorized sidebar space search by name, with empty results and restoration of the navigation tree when the query is cleared.

## Requirements
### Requirement: Search accessible spaces by name
The system SHALL provide GET /api/spaces/search with case-insensitive substring matching over accessible active spaces and page/size parameters.
#### Scenario: Matching spaces
- **WHEN** a user submits a non-empty query in the sidebar
- **THEN** matching accessible spaces are displayed as navigation results
#### Scenario: No matches
- **WHEN** no accessible space matches the query
- **THEN** the sidebar displays an explicit empty result message
#### Scenario: Clear search
- **WHEN** the search input becomes empty
- **THEN** the sidebar restores the space tree controls
#### Scenario: Guest permissions
- **WHEN** a guest searches for a space without READ permission
- **THEN** that space is excluded from the results
