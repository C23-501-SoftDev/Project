## Context
The main document API already checks draft visibility, but alternative representations check only the space. An unrelated reader can request exports directly.

## Goals / Non-Goals
Apply the existing author/admin draft rule consistently. Preserve published and deleted document policies, write permissions and file formats.

## Decisions
Add `DocumentService.canReadDocument` combining the existing space and status checks. Use it in method-security guards before rendering or accessing file/version data, including attachment downloads and history SSR. Controller/service changes only; repositories/database unchanged. Test all export formats with author, admin, reader, another editor and a guest lacking space rights. Verify attachment and version read parity.

## Risks / Trade-offs
Clients previously relying on the bypass receive 403. BACKTRACKER documents this change. Extra authorization must happen before conversion; negative tests use all three format endpoints. Sidebar tree visibility is handled by the separate lazy-loading change.
