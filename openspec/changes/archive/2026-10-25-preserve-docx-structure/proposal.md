## Why
DOCX export drops Markdown tables and nested lists, and its heading formatting is discarded. Feature ID: `docx-document-structure` (US5.1.3).

## Inputs
[DOCX requirements](https://github.com/C23-501-SoftDev/Docs/tree/main/documents/backlog-descriptions/E5%20Экспорт%20и%20Отчетность%20(Export)/F5.1%20Экспорт%20документов/US5.1.3%20Конвертация%20в%20DOCX).

## What Changes
- Preserve editable Word tables, heading styles and nested list numbering.
- Preserve inline emphasis, hyperlinks and code line breaks.
- Add POI round-trip regression tests.

## Capabilities
### New Capabilities
- `structured-docx-export`: preserve Markdown structure in editable DOCX output.
### Modified Capabilities
None.

## Impact
Service renderer and tests only; controller, repository and database contracts remain unchanged.
