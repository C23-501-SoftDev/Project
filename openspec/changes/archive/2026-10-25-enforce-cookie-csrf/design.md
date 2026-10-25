## Context
JWT is supplied automatically by the browser in an HttpOnly cookie. The existing CSRF repository/handler and frontend token helpers are present, but wildcard exclusions disable enforcement for all mutations.
## Goals / Non-Goals
Reject unsafe requests missing a valid CSRF token while preserving legitimate browser/API workflows. No token authentication redesign, JWT format change or database migration.
## Decisions
Security infrastructure: remove blanket exclusions and retain CookieCsrfTokenRepository with XSRF-TOKEN/X-XSRF-TOKEN. GET /login materializes the token, HTML forms send _csrf, API clients retain the cookie and mirror its token in the header. Protect login/logout too. Configure Swagger UI to use the same cookie/header names.
JWT is re-authenticated on every request; configure NullAuthenticatedSessionStrategy for this stateless flow so session-login CSRF rotation does not clear the token after every authenticated request. Logout explicitly clears both cookies. Regression verifies consecutive authenticated reads/writes with one valid token.
Frontend: merge custom headers before constructing final fetch options so the CSRF header survives; send it for direct AI POST requests. Service/repository/database unchanged.
Tests: legitimate MockMvc writes use Spring's CSRF request postprocessor; Playwright API clients obtain real server cookies and attach the header. Dedicated negative tests use the raw client and actual cookie repository to avoid masking enforcement bugs.
## Risks / Trade-offs
Existing API scripts that omit CSRF will receive 403 and must bootstrap the cookie. A request missing both authentication and CSRF can fail at the CSRF filter first; document this distinction. Server does not retry mutations automatically.
