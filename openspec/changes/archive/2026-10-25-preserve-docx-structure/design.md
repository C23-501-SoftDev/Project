## Context
The existing visitor ignores table cells, only reads paragraph children of list items, and creates then removes heading formatting runs.
## Goals / Non-Goals
Preserve editable semantic Word structures for supported Markdown. Image embedding, remote resource downloads and exact HTML layout reproduction are outside this change.
## Decisions
Service: extract a per-export DocxRenderer with no shared mutable document state. Use POI heading styles, native numbering definitions, table rows/cells and hyperlink relationships. Keep Markdown parsing in MarkdownService. Controller/repository/database contracts are unchanged.
Each list receives a numbering instance so separate lists restart correctly; Markdown start numbers and nesting are preserved up to Word's nine native levels. Deeper nesting remains readable at level nine. Raw HTML is not executed and external resources are never fetched. Unsafe hyperlink protocols are omitted.
## Risks / Trade-offs
Word viewers can substitute fonts; content structure is asserted by reopening the generated OOXML with POI. Embedded images and reference-style links remain a separate enhancement.
