## Why
Markdown raw HTML currently reaches document views, downloaded HTML and the editor preview with executable event handlers. HTML exports also lack standalone styles. Feature ID: `safe-markdown-html-export`.

## Inputs
[US5.1.1 Markdown-to-HTML requirements](https://github.com/C23-501-SoftDev/Docs/tree/main/documents/backlog-descriptions/E5%20Экспорт%20и%20Отчетность%20(Export)/F5.1%20Экспорт%20документов/US5.1.1%20Генерация%20HTML%20из%20Markdown).

## What Changes
- Sanitize rendered HTML with a conservative allowlist that preserves headings, tables, lists, links and attachment references.
- Provide styled standalone HTML exports.
- Sanitize browser preview and pin local Marked/DOMPurify assets with licenses.
- Prevent the PDF renderer from fetching arbitrary network or file resources referenced by Markdown.

## Capabilities
### New Capabilities
- `safe-markdown-html-export`: safe formatted HTML in previews, views and downloads.
### Modified Capabilities
None.

## Impact
MarkdownService, export controller and editor template; direct jsoup dependency, local JS assets and regression tests. No database changes. PDF image embedding/attachment archives are outside this change.
