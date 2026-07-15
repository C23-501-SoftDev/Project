## Summary

Automatically assign requirement numbers to newly added requirement rows when a document created from a template is saved.

## Problem

Requirement numbering currently runs when a document is initially created from a template. If a user later edits that document and adds new rows to a requirement table, those rows can remain unnumbered unless the user assigns numbers manually. Re-running the existing creation-time numbering on save would be unsafe because it can replace existing `REQ-XXX` identifiers.

## Proposed Change

- Extend requirement numbering so document updates can fill only missing requirement numbers.
- Preserve existing `REQ-XXX` values in requirement tables.
- Apply save-time numbering only to documents that were created from a template.
- Return the content that was actually saved from `PUT /api/documents/{id}` so the UI immediately receives newly assigned numbers.

## Impact

- Backend update flow changes for template-based documents.
- Requirement numbering service gains a mode for numbering only missing rows.
- Existing requirement numbers remain stable across edits.
- API clients may receive content different from the submitted payload when new numbers are assigned.
