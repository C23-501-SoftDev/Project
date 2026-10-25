## Context
PageController currently adds every document tree to every page. Most trees are hidden, yet all rows are queried, serialized and inserted in the DOM. The bulk tree path also exposes draft titles to unrelated readers.
## Goals / Non-Goals
Keep document page rendering independent of total document count in other spaces. Preserve tree ordering, active document highlighting, drag/drop, space search and existing navigation. This change does not paginate the space list or nodes within one huge space.
## Decisions
- Controller: initial pages provide space metadata only. GET /spaces/{id}/tree returns the existing escaped Thymeleaf fragment with no-store caching headers.
- Service: check space READ once, query one space, filter drafts by author/admin before building the ordered hierarchy. No per-document permission queries.
- Repository/database: reuse existing single-space batch query and indexes; no migration.
- Frontend: load current/selected space, observe other restored expanded trees as they enter the sidebar viewport. Cache per page and share in-flight promises. Errors show a retry button. Loading a destination tree precedes drag/drop root-position calculation.
- Avoid a global tree cache because authorization and document edits would require careful invalidation.
## Risks / Trade-offs
One exceptionally large space still requires one complete tree. Space list pagination and a production concurrency benchmark remain follow-up work. Local five-navigation measurements are evidence for the fixture, not a universal SLA.
