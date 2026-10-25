# portable-attachment-image-export Specification

## Purpose
Provide bounded, authorized offline attachment archives and embedded local raster images across document export formats.
## Requirements
### Requirement: Authorized portable attachment archive
The system SHALL provide a ZIP export containing offline HTML and all current-document attachments with a manifest and unique traversal-safe relative paths. Existing document read and draft restrictions MUST apply.
#### Scenario: Open exported archive offline
- **WHEN** an authorized user exports a document with images and files, extracts the ZIP and opens index.html without server access
- **THEN** supported images render and attachment links resolve to the bundled files
#### Scenario: Foreign attachment reference
- **WHEN** document markup references an attachment owned by another document or an inaccessible source
- **THEN** that file and its private metadata are not included and a generic fallback is shown

### Requirement: Embedded raster images across export formats
HTML, PDF and DOCX exports SHALL include validated PNG/JPEG images from the exported document's attachments while retaining existing text, headings, lists and tables.
#### Scenario: Inline and nested images
- **WHEN** an authorized document contains supported images in paragraphs, lists or tables
- **THEN** the exported formats contain visible bounded images at those locations

### Requirement: No arbitrary resource fetching
Export rendering MUST NOT fetch remote URLs or arbitrary local files, and HTML MUST remain sanitized before verified resource rewriting.
#### Scenario: Malicious markup or URL
- **WHEN** markup contains scripts, active content, remote image URLs or file paths
- **THEN** no code executes and no outbound/local arbitrary resource is read

### Requirement: Bounded and complete export
Export MUST enforce documented file, byte, image-count and decoded-pixel limits, return controlled errors for exceeded limits and missing required archive files, and avoid caching personalized responses.
#### Scenario: Limit exceeded or missing required file
- **WHEN** an export exceeds its resource budget or cannot read an attachment promised in the archive
- **THEN** the request returns a controlled failure instead of an incomplete successful artifact
#### Scenario: Authorization changes during rendering
- **WHEN** the user loses document access or included attachment membership changes while rendering
- **THEN** the finished export is discarded and no newly unauthorized content is returned

