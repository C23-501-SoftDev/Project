## Why
Every document page currently queries and renders all accessible document trees. A local 50-space/5000-document fixture adds about 2 MB to every page. Feature ID: `lazy-space-document-trees`, addressing [issue #23](https://github.com/C23-501-SoftDev/Project/issues/23) and US4.4.1.
## Inputs
[Performance backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json), US4.4.1 document viewing target of at most two seconds under normal load.
## What Changes
- Render space controls initially and request each tree only when needed.
- Guard the tree fragment by space access and draft visibility, preserve ordering and drag/drop.
- Deduplicate in-flight requests, cache loaded trees for the page and allow retry after failure.
- Record reproducible local before/after measurements and their limits.
## Capabilities
### New Capabilities
- `lazy-space-document-trees`: authorized tree loading independent of document page rendering.
### Modified Capabilities
None.
## Impact
PageController adds GET /spaces/{id}/tree; DocumentService filters a single space. Templates and main.js load fragments asynchronously. Repository APIs and database schema remain unchanged. BACKTRACKER is updated.
