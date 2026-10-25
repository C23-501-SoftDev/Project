# safe-markdown-html-export Specification

## Purpose
Provide formatted Markdown HTML while preventing execution of supplied markup and untrusted PDF resource access.
## Requirements
### Requirement: Rendered Markdown is safe and formatted
The system MUST preserve safe headings, emphasis, tables, lists and links while removing scripts, event handlers and executable URL schemes from rendered Markdown.

#### Scenario: Unsafe HTML in a document
- **WHEN** Markdown containing script elements, event handlers or javascript links is viewed, previewed or exported
- **THEN** no supplied script executes and safe surrounding content remains visible.

#### Scenario: Standalone HTML export
- **WHEN** a document is exported to HTML
- **THEN** the file contains UTF-8 metadata, an escaped title, sanitized Markdown and a local stylesheet preserving readable tables and lists.

### Requirement: PDF conversion does not fetch untrusted resources
The PDF renderer MUST deny arbitrary network and filesystem resources referenced by user-controlled markup.

#### Scenario: Remote image reference
- **WHEN** Markdown references an external image or stylesheet during PDF conversion
- **THEN** conversion does not request that resource and still exports supported text formatting.

