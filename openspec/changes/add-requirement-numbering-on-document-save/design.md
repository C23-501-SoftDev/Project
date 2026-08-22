## Context

Documents created from templates already store `template_id` and use `RequirementNumberService` during creation. The service identifies Markdown tables whose first header cell is `№`, `No`, or `N`, then writes `REQ-XXX` values into the first column. Counters are persisted per `(space_id, template_id)`.

The update path currently saves the submitted content directly and the controller returns `request.content`, so any server-side content changes during save would not be visible to the caller unless the response is changed.

## Decisions

### 1. Number only missing requirement identifiers on update

Add a save-time numbering path that inspects requirement table rows and allocates a new number only when the first cell is empty or otherwise not already a `REQ-XXX` value. Existing `REQ-001` style identifiers MUST be preserved.

This avoids renumbering stable references while still supporting new rows added by users.

### 2. Restrict save-time numbering to template documents

Apply the behavior only when the document has a non-null `templateId`. Documents not created from templates keep the exact submitted content.

### 3. Reuse the existing counter scope

New numbers continue to come from `requirement_number_counters` scoped by `(space_id, template_id)`. This keeps numbering consistent with the creation-time behavior and independent across spaces and templates.

### 4. Return actual saved content from update

After `DocumentService.updateDocument(...)` applies save-time numbering and writes content, `PUT /api/documents/{id}` should return the content read from storage or otherwise returned by the service as saved. The response must not blindly echo the request body.

## Edge Cases

- Requirement rows with existing `REQ-XXX` values are left unchanged.
- Multiple new rows in one save receive sequential numbers.
- Multiple requirement tables in the same document are processed in document order.
- Documents without `templateId` are not auto-numbered on save.
- Existing non-empty first-cell values that are not `REQ-XXX` should be treated deliberately during implementation; preferred behavior is to avoid overwriting user-entered values unless tests/specs require otherwise.

## Testing

- Update a template-created document with an existing numbered row and a new empty row; assert only the empty row receives the next number.
- Update a template-created document with multiple new rows; assert sequential allocation.
- Update a non-template document; assert content is unchanged.
- Verify `PUT /api/documents/{id}` response contains the actual saved numbered content.
