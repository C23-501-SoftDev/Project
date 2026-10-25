## ADDED Requirements
### Requirement: Independent sidebar scrolling
The sidebar SHALL remain within the viewport below navigation and SHALL scroll its tree or search results without moving the main content.
#### Scenario: Long document tree
- **WHEN** a user scrolls a tree taller than the viewport
- **THEN** the tree scroll position changes while the sidebar search and main content keep their position
#### Scenario: Main content scrolling
- **WHEN** the user scrolls long main content
- **THEN** the sidebar stays below navigation and its tree scroll position is preserved
