## Context
The existing controller exports text through MarkdownService and DocxRenderer. Attachments have SQL metadata and a local storage port, and document read permissions already protect drafts and spaces.
## Goals / Non-Goals
Provide offline HTML/ZIP and actual local raster images in PDF/DOCX while preserving existing structures and authorization. External image downloading, SVG/active documents as images and arbitrary filesystem paths are excluded.
## Decisions
- Add an application DocumentExportService to orchestrate document authorization, attachment membership, bounded blob reads, rendering and final authorization checks. Reuse repository/storage ports; no DB migration.
- Resolve only supported attachment URLs belonging to the exported document. Foreign IDs, external URLs and missing image references produce a generic visible fallback without exposing private metadata. Never fetch arbitrary HTTP/file resources.
- Sanitize HTML first, then rewrite image/link DOM attributes from verified resources. Standalone HTML uses validated PNG/JPEG data URIs and downloadable attachment data. ZIP contains `index.html`, `attachments/{id}-{safeFilename}` and `manifest.json`, using relative local links and unique traversal-safe paths.
- PDF uses a controlled in-memory image resolver; arbitrary resources remain blocked. DOCX uses Apache POI picture runs with bounded dimensions, preserving headings, tables, lists and Cyrillic fonts/text.
- Limits: at most 100 files, 10MiB per file, 50MiB aggregate, 100 image occurrences, 12 million pixels per image and 32 million aggregate decoded pixels. Validate actual PNG/JPEG content, not only declared MIME. Bounded fallbacks for unsupported images; missing required ZIP file returns controlled 409, resource limits 413.
- Add GET `/api/documents/{id}/export/zip` and an HTML-with-attachments ZIP option to the existing UI. Export responses use no-store. Existing HTML/PDF/DOCX routes remain compatible.
## Risks / Trade-offs
Image decoding and byte-array renderers use memory: enforce aggregate byte/pixel budgets and avoid repeated decoding. Invalid/missing images remain visible as fallback text; unsupported formats remain available as attachments. An export must not silently succeed with a missing file that the manifest promises. Re-check access/membership before returning the artifact; test permission changes and foreign attachment references.
## Migration Plan
No data migration. Existing attachments can be exported immediately. Rollback removes the added ZIP path and image-aware service without changing stored documents/blobs.
