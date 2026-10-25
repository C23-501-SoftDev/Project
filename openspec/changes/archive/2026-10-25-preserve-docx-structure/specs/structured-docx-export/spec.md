## ADDED Requirements
### Requirement: Editable Word document structure
DOCX export SHALL preserve Markdown headings, table cells and ordered/unordered nested lists as editable Word structures.
#### Scenario: Mixed document
- **WHEN** Markdown contains headings, a table and nested lists
- **THEN** the DOCX contains heading styles, a Word table with all cell text, and numbered paragraphs with appropriate nesting
#### Scenario: Numbered list starts above one
- **WHEN** an ordered list begins at 3
- **THEN** its native numbering starts at 3 and a later separate list can restart at 1
### Requirement: Preserve inline and code content
DOCX export SHALL preserve bold, italic, strikethrough, safe inline hyperlinks and explicit code line breaks without fetching external resources.
#### Scenario: Rich inline text and code
- **WHEN** a paragraph contains emphasis and a hyperlink and a code block contains multiple lines
- **THEN** the exported document retains the formatted text, safe link target and code line boundaries
