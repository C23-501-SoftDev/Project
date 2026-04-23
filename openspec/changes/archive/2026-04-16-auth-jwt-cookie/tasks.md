## 1. JWT Infrastructure Layer

- [x] 1.1 Implement `JwtTokenProvider` — HMAC-SHA256 token generation, validation, and claim extraction (userId, role, iat, exp)
- [x] 1.2 Implement `JwtCookieAuthenticationFilter` — extract JWT from HttpOnly "JWT" cookie, validate, look up user, set SecurityContext
- [x] 1.3 Implement `JwtAuthenticationEntryPoint` — 401 handler (JSON for API, redirect to /login for browser)
- [x] 1.4 Implement `JwtAccessDeniedHandler` — 403 handler (JSON for API, error page for browser)

## 2. Security Configuration

- [x] 2.1 Implement `SecurityConfig` with stateless session policy, endpoint access rules, and CSRF cookie-based configuration
- [x] 2.2 Register `JwtCookieAuthenticationFilter` in the filter chain before `UsernamePasswordAuthenticationFilter`
- [x] 2.3 Configure public endpoints: `/login`, `/error`, static resources, `/api/auth/login`, Swagger, actuator
- [x] 2.4 Configure admin-only endpoints: `/admin/**`, `/api/admin/**` with `hasRole("ADMIN")`
- [x] 2.5 Configure `PasswordEncoder` bean with BCrypt strength=10

## 3. Application Service Layer

- [x] 3.1 Implement `AuthService.authenticate(login, password)` — BCrypt verification, generic error messages for both wrong login and wrong password
- [x] 3.2 Implement `AuthService.getCurrentUser(userId)` — user lookup by ID from JWT

## 4. REST Controller Layer (SSR + REST)

- [x] 4.1 Implement `AuthController` with SSR endpoints: GET/POST `/login`, POST `/logout`
- [x] 4.2 Implement `AuthController` with REST endpoints: POST `/api/auth/login` (JSON login), GET `/api/auth/me` (current user)
- [x] 4.3 Implement `setJwtCookie()` helper — HttpOnly=true, Secure=false (dev), path="/", maxAge=86400
- [x] 4.4 Implement `clearJwtCookie()` helper — clears JWT and XSRF-TOKEN cookies

## 5. DTOs and Mappers

- [x] 5.1 Create `LoginRequest` DTO (validation: non-blank login and password)
- [x] 5.2 Create `LoginResponse` DTO (token + UserResponse)
- [x] 5.3 Create `UserResponse` DTO
- [x] 5.4 Ensure `RestDtoMapper` maps User → UserResponse

## 6. Swagger/OpenAPI Documentation

- [x] 6.1 Add `@Tag`, `@Operation`, `@ApiResponses` annotations to `AuthController` endpoints
- [x] 6.2 Configure `OpenApiConfig` with security scheme for JWT cookie authentication

## 7. Quality Gates

- [x] 7.1 Run all quality gates as defined in `openspec/quality-gates.md`
- [x] 7.2 Verify `Secure=false` cookie flag is documented as must-change-for-production
- [x] 7.3 Verify JWT secret is not hardcoded and uses property injection

## Out of Scope
- Automated unit/integration tests (separate team member responsibility)