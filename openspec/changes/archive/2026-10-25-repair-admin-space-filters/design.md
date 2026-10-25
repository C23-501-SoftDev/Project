## Context
The status control lacks `data-target`; owner options are replaced after listeners attach. Browser tests reproduce both issues.

## Goals / Non-Goals
Restore the existing status/owner query contract. No changes to controller/service/repository/database or authorization.

## Decisions
Delegate selection events to the options container so newly rendered options work without reinitializing controls. Bind the status field explicitly. Listen on the actual hidden input, reset owner immediately and suppress stale owner-list responses. Keep a focused searchable select open so typing and clicking remain usable. Use existing end-to-end filter scenarios as regression coverage.

## Risks / Trade-offs
Shared select behavior affects multiple pages; run the entire browser suite. Slow owner requests can overwrite newer status choices; use a request sequence and an empty/loading menu until the matching response arrives.
