## Why
The application authenticates with cookies but excludes all API writes, login and logout from CSRF validation, contradicting its safe-save specification. Feature ID: `cookie-api-csrf-protection`.
## Inputs
[Security backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json), existing `openspec/specs/add-safe-document-save/spec.md`.
## What Changes
- Enforce the configured cookie/header CSRF contract on unsafe requests, including login/logout.
- Preserve CSRF headers when callers supply custom headers and attach them to AI actions.
- Enable Swagger UI CSRF support and update API usage documentation.
- Update legitimate test clients to obtain/send real CSRF tokens; add explicit missing/invalid-token rejection regressions.
## Capabilities
### New Capabilities
- `cookie-api-csrf-protection`: enforce CSRF for cookie-authenticated mutation requests.
### Modified Capabilities
None.
## Impact
SecurityConfig, frontend HTTP helpers, AI forms, Swagger configuration, tests and BACKTRACKER. Existing request payloads, service, repository and database schema remain unchanged. API clients must bootstrap through GET /login before unsafe requests.
