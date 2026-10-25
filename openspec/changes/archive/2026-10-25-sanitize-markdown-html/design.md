## Context
Flexmark currently renders raw HTML without cleaning; Marked preview writes directly to innerHTML. HTML exports have no stylesheet. PDF rendering can resolve attacker-supplied resource URLs.

## Goals / Non-Goals
Preserve safe Markdown formatting and links while removing executable markup. No write to stored Markdown, no database/repository changes. Embedded attachment archives and authenticated PDF image resolution are separate work.

## Decisions
Use jsoup 1.23.2 as a direct dependency for server-side allowlist cleaning, emitting XHTML-compatible markup for OpenHTMLtoPDF. Preserve relative links using a fixed synthetic validation base and preserveRelativeLinks. Add trusted stylesheet and escaped title only after cleaning. Use local Marked 18.1.1 plus DOMPurify 3.4.17 in preview, preserving licenses. Block external PDF resources both before and after URI resolution and install a denying URI resolver: the PDFBox image loader in version 1.0.10 bypasses the resource predicates in the regression test. Application service/controller/frontend change; repository/database unchanged.

## Risks / Trade-offs
Raw scripts, inline styles and unsafe URI schemes are intentionally removed. Remote images remain available to browser HTML but PDF does not download external resources. Verify ordinary formatting, relative attachments, event-handler/script removal, PDF resource isolation and browser execution markers. Direct jsoup version replaces Flexmark's old transitive version; run all tests.
