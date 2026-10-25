## Why
Existing exports preserve document text and structure but omit embedded images and do not package attachments for offline use. The user requested completion of these remaining features on 2026-10-26. Feature ID: `portable-attachment-image-export`.

Inputs: [export backlog](https://github.com/C23-501-SoftDev/Docs/tree/main/documents/backlog-descriptions/E5%20Экспорт%20и%20Отчетность%20(Export)/F5.1%20Экспорт%20документов), [base requirements](https://github.com/C23-501-SoftDev/Docs/blob/main/base-requirements.md), including the formerly optional attachment archive now explicitly requested by the user.
## What Changes
- Embed authorized document PNG/JPEG images in HTML, PDF and DOCX.
- Add a ZIP export with offline HTML and document attachments, safe unique paths and a manifest.
- Preserve draft/read permissions, HTML sanitization and the prohibition on arbitrary network/file fetching.
- Add explicit resource limits, controlled failures, UI controls and regression coverage.
## Capabilities
### New Capabilities
- `portable-attachment-image-export`: portable exports with local images and attachments.
### Modified Capabilities
None: text structure, existing export routes and document read permissions remain compatible; this adds attachment-aware rendering.
## Impact
DocumentExportController, application export service, MarkdownService/DocxRenderer, attachment repository/storage ports, document export UI and tests. Adds GET /api/documents/{id}/export/zip; updates BACKTRACKER.md.
## Out of scope
Downloading remote images, embedding SVG/active content, arbitrary filesystem access, and inlining every file type as a picture.
