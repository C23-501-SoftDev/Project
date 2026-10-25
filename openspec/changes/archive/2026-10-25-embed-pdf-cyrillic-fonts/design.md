## Context
MarkdownService currently searches Linux DejaVu directories and silently falls back to fonts without Cyrillic coverage on other systems.

## Goals / Non-Goals
Goals: portable Cyrillic and Latin text in body, emphasis, headings, tables and code. Non-goals: complete Unicode coverage, remote images, PDF/A.

## Decisions
Service: register six licensed Noto Sans/Mono TTF resources through classpath stream suppliers; subset and embed fonts in each PDF. Pin upstream commit and record checksums/license. This is deterministic across operating systems and works inside the executable JAR. OS font discovery was rejected because deployment then changes document content.
Controller, repository and database contracts remain unchanged. CSS selects bundled proportional and monospace families. External URI loading remains denied.

## Risks / Trade-offs
- JAR grows by about 3 MB; subsetting keeps exported documents smaller.
- Font coverage excludes some scripts and emoji; promise only tested Cyrillic and Latin.
- Missing packaged fonts must fail visibly instead of silently corrupting text.

## Validation
PDFBox extracts exact Russian text and confirms embedded fonts. Render the generated fixture with Poppler and inspect page images. Run Maven clean verify, Node tests and all Playwright tests against PostgreSQL.
