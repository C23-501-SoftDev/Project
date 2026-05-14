## MODIFIED Requirements

### Requirement: JWT Token Generation
**Reason:** Role model changed from ADMIN/EDITOR/READER to GUEST/READER/EDITOR + is_admin flag. JWT needs to carry is_admin claim.

The system SHALL generate signed JWT tokens upon successful user authentication. The token MUST use HMAC-SHA256 (HS256) algorithm and contain claims: `sub` (user ID as string), `userId` (Long), `role` (global role DB value: "Guest", "Reader", "Editor"), `isAdmin` (boolean), `iat` (issued at), and `exp` (expiration time). Token expiration MUST be configurable via `app.jwt.expiration-ms` property. For backward compatibility, the token generation SHALL include `role` as the DB value of the user's GlobalRole and `isAdmin` from the user's isAdmin field.

#### Scenario: Successful token generation after login
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

## ADDED Requirements

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
