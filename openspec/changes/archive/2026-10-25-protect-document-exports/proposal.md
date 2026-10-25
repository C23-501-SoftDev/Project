## Why
Space READ permission currently allows exporting another author's draft. The same bypass exists in version and attachment reads. All representations must follow the document's existing draft visibility rule.

Feature ID: `document-draft-read-permissions`.
Requirements: [US2.1.1 Реализация статусов Draft и Published](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog-descriptions/E2%20%D0%96%D0%B8%D0%B7%D0%BD%D0%B5%D0%BD%D0%BD%D1%8B%D0%B9%20%D1%86%D0%B8%D0%BA%D0%BB%20%D0%B8%20%D0%92%D0%B5%D1%80%D1%81%D0%B8%D0%BE%D0%BD%D0%BD%D0%BE%D1%81%D1%82%D1%8C%20%28Lifecycle%20%26%20Versioning%29/F2.1%20Workflow%20%D0%B4%D0%BE%D0%BA%D1%83%D0%BC%D0%B5%D0%BD%D1%82%D0%B0/US2.1.1%20%D0%A0%D0%B5%D0%B0%D0%BB%D0%B8%D0%B7%D0%B0%D1%86%D0%B8%D1%8F%20%D1%81%D1%82%D0%B0%D1%82%D1%83%D1%81%D0%BE%D0%B2%20Draft%20%D0%B8%20Published/description.md), [US5.1.1 Генерация HTML из Markdown](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog-descriptions/E5%20%D0%AD%D0%BA%D1%81%D0%BF%D0%BE%D1%80%D1%82%20%D0%B8%20%D0%9E%D1%82%D1%87%D0%B5%D1%82%D0%BD%D0%BE%D1%81%D1%82%D1%8C%20%28Export%29/F5.1%20%D0%AD%D0%BA%D1%81%D0%BF%D0%BE%D1%80%D1%82%20%D0%B4%D0%BE%D0%BA%D1%83%D0%BC%D0%B5%D0%BD%D1%82%D0%BE%D0%B2/US5.1.1%20%D0%93%D0%B5%D0%BD%D0%B5%D1%80%D0%B0%D1%86%D0%B8%D1%8F%20HTML%20%D0%B8%D0%B7%20Markdown/description.md).

## What Changes
- Require both space access and document visibility before HTML/PDF/DOCX generation, version/diff reads, and attachment reads/downloads.
- Keep authors and administrators able to export their drafts; preserve published-document reading.
- Document the actual format-specific export endpoints and 403 behavior.

## Capabilities
### New Capabilities
- `document-draft-read-permissions`: consistent read authorization for document representations.
### Modified Capabilities
None.

## Impact
DocumentService, export/version/attachment controllers, attachment download guard, history page guard and BACKTRACKER. No schema changes.
