# jwt-cookie-authentication Specification

## Purpose
TBD - created by archiving change auth-jwt-cookie. Update Purpose after archive.

## Requirements

### Requirement: JWT Token Generation
The system SHALL generate signed JWT tokens upon successful user authentication. The token MUST use HMAC-SHA256 (HS256) algorithm and contain claims: `sub` (user ID as string), `userId` (Long), `role` (global role DB value: "Guest", "Reader", "Editor"), `isAdmin` (boolean), `iat` (issued at), and `exp` (expiration time). Token expiration MUST be configurable via `app.jwt.expiration-ms` property. For backward compatibility, the token generation SHALL include `role` as the DB value of the user's GlobalRole and `isAdmin` from the user's isAdmin field.

#### Scenario: Successful token generation after login with isAdmin claim
- **WHEN** a user provides valid login and password credentials
- **THEN** the system generates a JWT containing userId, role ("Guest"/"Reader"/"Editor"), isAdmin (boolean), and expiration claims signed with HMAC-SHA256

#### Scenario: Token contains isAdmin claim
- **WHEN** an admin user (isAdmin=true) with role EDITOR logs in
- **THEN** the JWT contains role="Editor" and isAdmin=true

#### Scenario: Token contains is_admin false for non-admin
- **WHEN** a regular user (isAdmin=false) with role READER logs in
- **THEN** the JWT contains role="Reader" and isAdmin=false

#### Scenario: Token expiration is configurable
- **WHEN** the server starts with `app.jwt.expiration-ms=86400000` (24 hours)
- **THEN** generated tokens expire 24 hours after their `iat` timestamp

### Requirement: JWT Token Validation
The system SHALL validate JWT tokens on every authenticated request. Validation MUST check: signature integrity, token expiration, token format (well-formed JWT), and non-empty content. Invalid tokens MUST be rejected silently (request continues without authentication).

#### Scenario: Valid token is accepted
- **WHEN** a request carries a JWT with valid signature, non-expired timestamp, and correct format
- **THEN** the token is considered valid and the user is authenticated

#### Scenario: Expired token is rejected
- **WHEN** a request carries a JWT whose `exp` timestamp has passed
- **THEN** the token is rejected with a warning log entry and the request continues unauthenticated

#### Scenario: Tampered token is rejected
- **WHEN** a request carries a JWT with an altered payload or invalid signature
- **THEN** the token is rejected with a warning log about signature mismatch

#### Scenario: Empty token is ignored
- **WHEN** the JWT cookie is present but empty or null
- **THEN** the token is ignored and the request continues without authentication

### Requirement: JWT Cookie Transport
The system SHALL store the JWT token in an HttpOnly cookie named `JWT` with path `/` and a maximum age of 24 hours. The cookie MUST be HttpOnly to prevent JavaScript access, preventing XSS token theft. The `Secure` flag MUST be configurable (false for development, true for production with HTTPS).

#### Scenario: Login sets HttpOnly JWT cookie
- **WHEN** a user successfully authenticates via POST /login or POST /api/auth/login
- **THEN** an HttpOnly cookie named "JWT" is set with the generated token, path "/", and maxAge=86400

#### Scenario: Cookie is not accessible via JavaScript
- **WHEN** client-side JavaScript attempts to read document.cookie for the "JWT" cookie
- **THEN** the cookie value is not visible due to the HttpOnly flag

#### Scenario: Logout clears the JWT cookie
- **WHEN** a user logs out via POST /logout
- **THEN** the "JWT" cookie is cleared (maxAge=0) and the XSRF-TOKEN cookie is also cleared

### Requirement: Cookie-Based Authentication Filter
The system SHALL extract the JWT token from the `JWT` cookie on every incoming HTTP request, validate it, look up the user from the database by userId claim, and establish Spring Security authentication context. The system SHALL set authorities based on: (1) the user's global role → ROLE_GUEST/ROLE_READER/ROLE_EDITOR, and (2) if `isAdmin=true`, additionally ROLE_ADMIN. The filter MUST execute once per request (`OncePerRequestFilter`) and MUST NOT block requests when the token is absent or invalid.

#### Scenario: Admin user gets ROLE_ADMIN authority
- **WHEN** a request includes a valid "JWT" cookie with userId=5 and isAdmin=true
- **THEN** SecurityContext contains Authentication with the user object and ADMIN authority (plus their role authority)

#### Scenario: Regular user does not get ROLE_ADMIN authority
- **WHEN** a request includes a valid "JWT" cookie with userId=3 and isAdmin=false, role="Reader"
- **THEN** SecurityContext contains Authentication with READER authority but NOT ADMIN authority

#### Scenario: Valid cookie establishes authentication
- **WHEN** a request includes a valid "JWT" cookie with userId=5 and role="Guest"
- **THEN** SecurityContext contains an Authentication with the user object and ROLE_GUEST authority

#### Scenario: Missing cookie allows unauthenticated request
- **WHEN** a request has no "JWT" cookie
- **THEN** the filter passes the request through without setting authentication (endpoint security rules still apply)

#### Scenario: User not found in database rejects authentication
- **WHEN** a valid JWT cookie references a userId that does not exist in the database
- **THEN** the filter does not establish authentication and the request continues unauthenticated

### Requirement: Login Form (SSR) Endpoint
The system SHALL provide a GET /login endpoint that returns the Thymeleaf login page (HTML), and a POST /login endpoint that accepts `username` and `password` form parameters, authenticates the user, sets the JWT cookie, and redirects to the home page ("/"). Invalid credentials MUST display the login page with an error message.

#### Scenario: GET /login returns login page
- **WHEN** an unauthenticated user navigates to GET /login
- **THEN** the system returns the Thymeleaf login page HTML with a login form

#### Scenario: POST /login with valid credentials redirects home
- **WHEN** a user submits POST /login with valid username and password
- **THEN** the system sets the JWT cookie and redirects to "/"

#### Scenario: POST /login with invalid credentials shows error
- **WHEN** a user submits POST /login with incorrect username or password
- **THEN** the system redirects to /login?error and the page displays "Неверный логин или пароль"

### Requirement: REST API Login Endpoint
The system SHALL provide a POST /api/auth/login endpoint that accepts a JSON body with `login` and `password` fields, authenticates the user, sets the JWT cookie, and returns a JSON response containing the token and user information. The endpoint MUST be publicly accessible (no authentication required).

#### Scenario: REST login with valid credentials returns user data
- **WHEN** a client POSTs to /api/auth/login with {"login": "admin", "password": "admin123"}
- **THEN** the system responds with 200 OK containing {token: "...", user: {...}} and sets the JWT cookie

#### Scenario: REST login with invalid credentials returns 401
- **WHEN** a client POSTs to /api/auth/login with incorrect credentials
- **THEN** the system responds with 401 Unauthorized and an error message

#### Scenario: REST login with missing fields returns 400
- **WHEN** a client POSTs to /api/auth/login with an empty or malformed body
- **THEN** the system responds with 400 Bad Request and validation error details

### Requirement: Current User Endpoint
The system SHALL provide a GET /api/auth/me endpoint that returns the authenticated user's information as JSON. The endpoint MUST only respond 200 when the user is authenticated (valid JWT cookie present). Unauthenticated requests MUST receive 401.

#### Scenario: GET /api/auth/me returns current user
- **WHEN** an authenticated user GETs /api/auth/me with a valid JWT cookie
- **THEN** the system responds with 200 OK containing the user's JSON representation (id, login, role)

#### Scenario: GET /api/auth/me without authentication returns 401
- **WHEN** an unauthenticated request is made to GET /api/auth/me
- **THEN** the system responds with 401 Unauthorized (JSON for API requests, redirect to /login for browser requests)

### Requirement: Password Verification with BCrypt
The system SHALL verify user passwords using BCrypt hashing (strength=10). Authentication failures MUST NOT disclose whether the login name or the password was incorrect — a single generic error message MUST be used for both cases.

#### Scenario: Correct password authenticates user
- **WHEN** a user provides a valid login and matching password
- **THEN** BCrypt.matches() returns true and the user is authenticated

#### Scenario: Incorrect password fails with generic error
- **WHEN** a user provides a valid login but wrong password
- **THEN** authentication fails with InvalidCredentialsException and the error message does not specify "wrong password"

#### Scenario: Non-existent login fails with same generic error
- **WHEN** a user provides a login name that does not exist in the database
- **THEN** authentication fails with the same InvalidCredentialsException as a wrong password

### Requirement: CSRF Protection for AJAX
The system SHALL enable CSRF protection using Spring Security's CookieCsrfTokenRepository with `HttpOnly=false` for the XSRF-TOKEN cookie. The CSRF token MUST be included in responses for endpoints that require it. The login and logout endpoints (/login POST, /logout POST) and all /api/** endpoints SHALL be excluded from CSRF validation.

#### Scenario: XSRF-TOKEN cookie is set and readable by JavaScript
- **WHEN** a response includes a CSRF token
- **THEN** an XSRF-TOKEN cookie is set with HttpOnly=false so JavaScript can read it for AJAX request headers

#### Scenario: API endpoints are exempt from CSRF
- **WHEN** a POST request is made to /api/auth/login without a CSRF header
- **THEN** the request is processed normally (CSRF check is skipped for /api/**)

### Requirement: Endpoint Access Control
The system SHALL enforce the following access rules:
- PUBLIC: `/login`, `/error`, `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`, `/api/auth/login` (POST)
- AUTHENTICATED: All endpoints not explicitly public or admin-only
- ADMIN ONLY: `/admin/**`, `/api/admin/**`
- SWAGGER PUBLIC: `/swagger-ui/**`, `/swagger-ui.html`, `/api-docs/**`, `/api-docs`, `/actuator/health`

#### Scenario: Public endpoint accessible without login
- **WHEN** an unauthenticated user accesses GET /login
- **THEN** the request succeeds with 200 OK

#### Scenario: Protected endpoint requires authentication
- **WHEN** an unauthenticated user tries to access a protected endpoint
- **THEN** the system returns 401 Unauthorized

#### Scenario: Admin-only endpoint rejects non-admin users
- **WHEN** an authenticated non-admin user (Editor or Reader) tries to access /api/admin/**
- **THEN** the system returns 403 Forbidden

#### Scenario: Admin-only endpoint allows admin
- **WHEN** an authenticated Admin user accessing /api/admin/**
- **THEN** the request is allowed to proceed

### Requirement: 401/403 Error Response Modes
The system SHALL respond with JSON error bodies for API/AJAX requests (identified by `Accept: application/json` header or URI starting with `/api/`), and with browser redirects or standard error pages for non-API requests. 401 responses SHALL redirect browser users to /login. 403 responses SHALL show a "Forbidden" error.

#### Scenario: API request receives JSON 401
- **WHEN** an unauthenticated request with Accept: application/json hits a protected endpoint
- **THEN** the system returns 401 with JSON body containing timestamp, status, error, message, and path

#### Scenario: Browser request receives redirect 401
- **WHEN** an unauthenticated browser request (no Accept: application/json, non-API URI) hits a protected endpoint
- **THEN** the system redirects to /login

#### Scenario: API request receives JSON 403
- **WHEN** an authenticated but unauthorized request hits /api/admin/** 
- **THEN** the system returns 403 with JSON body containing "Недостаточно прав для выполнения данной операции"

### Requirement: Role Values Backward Compatibility
The system SHALL support reading JWT tokens that contain the legacy role value "Admin" for backward compatibility during migration. When `GlobalRole.fromDbValue("Admin")` is called, it SHALL return `EDITOR` to maintain continuity until all active sessions expire. This is a temporary compatibility measure.

#### Scenario: Legacy "Admin" role in JWT maps to EDITOR
- **WHEN** a JWT token with claim role="Admin" (legacy format) is validated
- **THEN** the system interprets the role as EDITOR and continues authentication normally

### Requirement: GUEST Role Authority
The system SHALL recognize `GUEST` as a valid global role. Users with role GUEST SHALL receive the authority ROLE_GUEST. GUEST users can only authenticate and have no global content permissions — they receive access only through SpaceUserPermission or SpaceGroupPermission.

#### Scenario: GUEST user authenticates successfully
- **WHEN** a user with role=GUEST and isAdmin=false logs in
- **THEN** the JWT contains role="Guest", isAdmin=false, and the user receives ROLE_GUEST authority

#### Scenario: GUEST user accesses admin panel is denied
- **WHEN** a GUEST user (isAdmin=false) attempts to access /api/admin/users
- **THEN** the system returns 403 Forbidden
