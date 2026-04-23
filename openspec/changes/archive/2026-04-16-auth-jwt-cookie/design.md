## Context

The Knowledge Base application required authentication before any admin or RBAC features could function. The implementation uses JWT tokens stored in HttpOnly cookies instead of the more common Authorization header pattern. This choice was driven by the need to support both SSR (Thymeleaf server-side rendering) and REST API (AJAX from Tiptap Editor) from the same endpoints.

**Architecture**: Clean/Hexagonal architecture with 4 layers:
- `infrastructure/security/jwt/` — JWT token operations (JwtTokenProvider, filter, error handlers)
- `infrastructure/security/config/` — SecurityConfig (Spring Security filter chain)
- `application/service/AuthService` — Business logic for authentication
- `interfaces/rest/controller/AuthController` — HTTP endpoints (SSR + REST)

**Constraints**:
- Must work with existing Spring Boot 3.x + Spring Security stack
- Must support both SSR form login and REST JSON login
- Must be compatible with Tiptap Editor AJAX requests (requires CSRF token)
- Database: PostgreSQL with existing `users` table

## Goals / Non-Goals

**Goals:**
- Secure user authentication via login/password with BCrypt hashing
- JWT-based stateless session management
- XSS-resistant token storage (HttpOnly cookie)
- CSRF protection for AJAX requests
- Dual-mode endpoints: SSR redirect flow + REST JSON flow
- Proper 401/403 error responses (JSON for API, redirect for browser)

**Non-Goals:**
- OAuth/SSO integration (not in scope)
- Password reset via email flow
- Multi-factor authentication (MFA)
- Token refresh mechanism (JWT expires after 24h, user must re-login)
- Session-based auth (explicitly rejected in favor of JWT)

## Decisions

### Decision 1: JWT in HttpOnly Cookie vs Authorization Header

**Chosen**: JWT stored in HttpOnly cookie named `JWT`

**Rationale**:
- HttpOnly cookies are not accessible via JavaScript, preventing XSS attacks from stealing tokens
- LocalStorage is XSS-vulnerable — any compromised script can read stored tokens
- Cookies are sent automatically by the browser, simplifying client-side code (no manual header construction)
- Works naturally with SSR redirects (no special client-side token handling needed)
- Tiptap Editor AJAX requests work with cookies without additional code

**Trade-offs**:
- Requires CSRF protection (implemented via Spring Security cookie-based CSRF token with `XSRF-TOKEN` cookie, `HttpOnly=false` so JavaScript can read it for AJAX headers)
- Cookie domain/path configuration needed for cross-path consistency
- Less suitable for mobile/external API consumers (which would prefer Bearer tokens)

### Decision 2: HMAC-SHA256 (HS256) Signing Algorithm

**Chosen**: Symmetric HMAC-SHA256 via `jjwt` library

**Rationale**:
- Simpler than asymmetric (RSA/ECDSA) — single secret key for sign + verify
- Appropriate for single-service deployment (no distributed token verification across services)
- Secret key configurable via `app.jwt.secret-key` application property
- Key derivation: `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` — standard JJWT approach

**Trade-offs**:
- Symmetric means the signing secret must be protected on the server
- Not suitable for microservice architectures where services share JWTs (would need asymmetric keys)

### Decision 3: Stateless Session (No Spring Session)

**Chosen**: `SessionCreationPolicy.STATELESS`

**Rationale**:
- JWT is self-contained — no server-side session state needed
- Horizontally scalable — no sticky sessions or shared session store required
- Reduces server memory footprint

**Trade-offs**:
- Cannot invalidate individual tokens before expiration (must rotate secret to invalidate all)
- Token contents (role, userId) are frozen until next login

### Decision 4: Dual CSRF Strategy

**Chosen**: Cookie-based CSRF token for API, ignored for login/logout

**Rationale**:
- Tiptap Editor makes AJAX requests that need CSRF protection
- Spring Security's `CookieCsrfTokenRepository.withHttpOnlyFalse()` creates `XSRF-TOKEN` cookie readable by JavaScript
- Login form (POST `/login`) is excluded — CSRF on login page is not a security concern (forged login doesn't compromise anything)
- `/api/**` endpoints are excluded from CSRF check — this is a trade-off: the API relies on JWT cookie validation alone

**Trade-offs**:
- `/api/**` CSRF exclusion is a minor risk — mitigated by the fact that the JWT cookie is HttpOnly (can't be forged by a third-party site)
- `Secure=false` on cookies in dev — must be changed to `true` in production

### Decision 5: Error Handling — Dual Mode (JSON vs Redirect)

**Chosen**: `JwtAuthenticationEntryPoint` and `JwtAccessDeniedHandler` detect request type

**Detection logic**:
- AJAX: `Accept: application/json` header OR `X-Requested-With: XMLHttpRequest`
- API: URI starts with `/api/`
- If either matches → return JSON error body
- Otherwise → redirect browser to `/login` (401) or show error (403)

**JSON response format** (for both 401 and 403):
```json
{
  "timestamp": "2024-01-01T00:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "...",
  "path": "/api/..."
}
```

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| JWT secret stored in properties file | Medium | Use environment variables or secrets manager in production |
| `Secure=false` cookie in development | Low (dev only) | Must set `Secure=true` before deploying to production with HTTPS |
| No token revocation before expiry | Medium | Accept 24h window; can rotate secret for emergency full invalidation |
| Role cached in JWT — changes require re-login | Low | Document that role changes take effect after next login |
| CSRF excluded on `/api/**` | Low | JWT HttpOnly cookie cannot be forged by cross-site requests; stateless API is inherently CSRF-resistant for same-origin |
| Fixed 24h token lifetime | Low | Configurable via `app.jwt.expiration-ms` property |
