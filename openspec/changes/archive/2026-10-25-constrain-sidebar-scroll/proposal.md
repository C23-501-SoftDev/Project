## Why
A long document tree grows the whole page and scrolls the main document list away. Feature ID: `sidebar-independent-scroll`. Implements [issue #20](https://github.com/C23-501-SoftDev/Project/issues/20).
## Inputs
[Document management backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json).
## What Changes
- Keep sidebar within the viewport below navigation.
- Scroll its tree/search results independently while keeping sidebar search available.
- Verify wheel scrolling in the browser with a long tree and long main content.
## Capabilities
### New Capabilities
- `sidebar-independent-scroll`: separate sidebar scrolling from main content.
### Modified Capabilities
None.
## Impact
Frontend CSS and browser regression test; controller/service/repository/database unchanged.
