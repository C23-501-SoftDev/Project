## Context
Sidebar overflow is set to auto but its height is unbounded, so it stretches the page.
## Goals / Non-Goals
Independent tree/search result scrolling, visible navigation and sidebar search. No redesign of mobile navigation or document lists.
## Decisions
CSS: sticky sidebar below the 64px navbar; viewport-bounded height, flex column, scrollable tree/results with min-height zero. Retain normal main-content scrolling. No controller, service, repository or DB changes.
## Risks / Trade-offs
Viewport units use vh fallback and dvh where supported. Regression exercises wheel events and verifies main-content position remains unchanged while the tree scrolls.
