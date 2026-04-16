## Why

The Knowledge Base application requires secure user authentication as the foundation for all future admin and RBAC capabilities. JWT-based authentication with HttpOnly cookies provides a stateless, secure approach that prevents XSS attacks from stealing tokens while supporting both SSR (Thymeleaf) page rendering and REST API (AJAX from Tiptap Editor) workflows.

Implements feature `auth-jwt-login` from `openspec/feature-registry.json`.
Based on docs: `D:/Soft-dev/Project/backend/readme.md`

## What Changes

- **NEW**: JWT token generation and validation via `JwtTokenProvider` (HMAC-SHA256, 24h expiration)
- **NEW**: JWT stored in HttpOnly Cookie named `JWT` instead of Authorization header — prevents XSS token theft
- **NEW**: `JwtCookieAuthenticationFilter` extracts token from cookie on every request and sets Spring Security context
- **NEW**: `SecurityConfig` with stateless session management, CSRF cookie-based token for AJAX, endpoint security rules
- **NEW**: SSR endpoints: `POST /login` (form login → JWT cookie → redirect), `POST /logout` (clear cookie)
- **NEW**: REST endpoints: `POST /api/auth/login` (JSON login, returns user + sets cookie), `GET /api/auth/me` (current user info)
- **NEW**: `AuthService.authenticate()` with BCrypt password verification, fails without disclosing whether login or password was wrong
- **NEW**: Error handlers: `JwtAuthenticationEntryPoint` (401), `JwtAccessDeniedHandler` (403)
- **NEW**: Public access for `/login`, `/api/auth/login`, `/swagger-ui/**`, `/api-docs/**`; admin-only for `/admin/**`, `/api/admin/**`

## Capabilities

### New Capabilities
- `jwt-cookie-authentication`: JWT-based authentication with HttpOnly cookie transport. Covers token generation, validation, cookie extraction, security context setup, SSR + REST login/logout flows, and CSRF protection for AJAX.

### Modified Capabilities
- *(none — this is the first capability)*

## Impact

- **Backend layers**: All 4 layers affected — `infrastructure/security/jwt/*`, `infrastructure/security/config/*`, `application/service/AuthService`, `interfaces/rest/controller/AuthController`
- **Dependencies**: `io.jsonwebtoken` (jjwt-api, jjwt-impl, jjwt-jackson) for JWT; `spring-boot-starter-security` for Spring Security
- **External interfaces**:
  - SSR: `/login` (GET/POST), `/logout` (POST)
  - REST: `POST /api/auth/login`, `GET /api/auth/me`
- **Security**: Cookies are HttpOnly but Secure=false (must be set to true in production with HTTPS)
- **Breaking**: *(none — this is the initial auth implementation)*
