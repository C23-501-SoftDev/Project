## 1. Requirement Numbering Service

- [x] 1.1 Add a method or mode that numbers only missing requirement rows in Markdown requirement tables.
- [x] 1.2 Preserve existing first-cell values that already match `REQ-XXX`.
- [x] 1.3 Allocate new numbers from the existing `(space_id, template_id)` counter for each newly numbered row.

## 2. Document Update Flow

- [x] 2.1 In `DocumentService.updateDocument`, apply missing-row numbering before saving content when the document has `templateId`.
- [x] 2.2 Keep non-template document saves unchanged.
- [x] 2.3 Ensure the content committed to storage is the content after numbering.

## 3. REST Response

- [x] 3.1 Update `PUT /api/documents/{id}` response to return the content actually saved by the service.
- [x] 3.2 Avoid echoing stale `request.content` when server-side numbering changed the payload.

## 4. Tests

- [x] 4.1 Add integration/unit coverage for adding a new empty requirement row to a template-created document.
- [x] 4.2 Assert existing `REQ-XXX` values are not modified on save.
- [x] 4.3 Assert multiple new rows receive sequential numbers.
- [x] 4.4 Assert non-template documents are not modified by save-time numbering.
- [x] 4.5 Assert update API response includes the newly assigned requirement number.

## 5. Quality Gates

- [x] 5.1 Run relevant backend tests for document update and requirement numbering.
- [ ] 5.2 Run OpenSpec validation/status for this change when the CLI is available.
