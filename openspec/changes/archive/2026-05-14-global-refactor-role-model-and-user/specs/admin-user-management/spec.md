## MODIFIED Requirements

### Requirement: Create User
**Reason:** Role model changed — ADMIN is no longer a valid role value. Need to support is_admin flag.

The system SHALL allow an administrator to create a new user by providing login, email, password, role, and isAdmin flag. The role MUST be one of GUEST, READER, or EDITOR (ADMIN is no longer valid). The isAdmin flag is a boolean (default false). The system MUST validate login uniqueness, email uniqueness, and password strength (min 6 chars). The password MUST be hashed with BCrypt before storage. Default role is READER if null. Default isAdmin is false if not specified. Upon successful creation, a `UserCreatedEvent` MUST be published with userId, email, and login.

#### Scenario: Successful user creation with is_admin
- **WHEN** an admin POSTs to /api/admin/users with valid login, email, password, role=EDITOR, and isAdmin=true
- **THEN** the system creates the user with BCrypt-hashed password, isAdmin=true, role=EDITOR, publishes UserCreatedEvent, and responds with 201 and the user data

#### Scenario: Successful user creation with GUEST role
- **WHEN** an admin POSTs to /api/admin/users with role=GUEST and isAdmin=false
- **THEN** the system creates the user successfully with role=GUEST and isAdmin=false

#### Scenario: Invalid role ADMIN rejected
- **WHEN** an admin POSTs to /api/admin/users with role=ADMIN
- **THEN** the system responds with 400 Bad Request with validation error "Invalid role value"

### Requirement: Update User Profile
**Reason:** Need to support is_admin flag in updates. ADMIN role no longer valid.

The system SHALL allow an administrator to update a user's login, email, role, and isAdmin flag. All fields are optional (null = no change). The role MUST be one of GUEST, READER, or EDITOR. The isAdmin is a boolean. When login or email is changed, uniqueness MUST be re-validated against other users (including soft-deleted users). The password MUST NOT be updated through this endpoint.

#### Scenario: Successful role change to GUEST
- **WHEN** an admin PUTs to /api/admin/users/{id} with role=GUEST
- **THEN** the user's role is set to GUEST and the system responds with 200

#### Scenario: Successful toggle is_admin flag
- **WHEN** an admin PUTs to /api/admin/users/{id} with isAdmin=true
- **THEN** the user's isAdmin flag is set to true and the system responds with 200
- **THEN** the new isAdmin value takes effect only after the user's next authentication (isAdmin is encoded in JWT)

#### Scenario: Login uniqueness check includes soft-deleted users
- **WHEN** an admin updates a user's login to match a soft-deleted user's login
- **THEN** the system responds with 409 Conflict

### Requirement: Delete User
**Reason:** Changed from hard-delete to soft-delete. Referential integrity checks no longer needed (user stays in DB).

The system SHALL soft-delete a user by setting `is_deleted = true` instead of physically removing them. The user's data remains in the database to preserve history. The user's login and email remain reserved. The deleted user SHALL NOT be able to authenticate. No referential integrity checks are needed (user physically remains). The system SHALL set `updated_at` to the current timestamp.

#### Scenario: Soft-delete succeeds for user with documents
- **WHEN** an admin DELETEs /api/admin/users/{id} for a user who is the author of documents
- **THEN** the system sets `is_deleted = true` (user remains in DB) and responds with 200 OK with updated user data

#### Scenario: Soft-delete sets updated_at
- **WHEN** an admin DELETEs /api/admin/users/{id}
- **THEN** the user's `updated_at` timestamp is updated to the current time

### Requirement: List Users with Pagination
**Reason:** Need to support includeDeleted parameter and isDeleted field in response.

The system SHALL allow an administrator to retrieve registered users paginated. By default, only active users (`is_deleted = false`) SHALL be returned. The endpoint MUST support an optional query parameter `includeDeleted` (default false) to include soft-deleted users. The endpoint MUST support: page number (0-based, default 0), page size (default 20), sort field (default "createdAt"), and sort direction (default "desc"). The response MUST include the list of users and metadata for pagination (total count, current page, page size). Each user response SHALL include `isDeleted` and `isAdmin` fields.

#### Scenario: Default list excludes deleted users
- **WHEN** an admin GETs /api/admin/users (no includeDeleted parameter)
- **THEN** the response includes only active users (is_deleted = false)

#### Scenario: includeDeleted=true includes deleted users
- **WHEN** an admin GETs /api/admin/users?includeDeleted=true
- **THEN** the response includes both active and soft-deleted users

### Requirement: Get User by ID
**Reason:** Need to support retrieving soft-deleted users in admin panel.

The system SHALL allow an administrator to retrieve a single user's details by ID. If the user exists and is active, return their full data (excluding password hash). If the user is soft-deleted, return their data with `isDeleted=true`. If the user does not exist, return 404. Admin users viewing user details SHOULD be able to see both active and deleted users.

#### Scenario: Get soft-deleted user by ID
- **WHEN** an admin GETs /api/admin/users/{id} for a soft-deleted user
- **THEN** the system returns 200 with the user's data including `isDeleted=true`

## REMOVED Requirements

### Requirement: Delete User with Referential Integrity
**Reason:** Replaced by soft-delete. User data physically remains in DB, so referential integrity checks for hard-delete are no longer needed.
**Migration:** The DELETE endpoint now performs soft-delete. All scenarios related to hard-delete referential integrity checks (documents, spaces, versions) are removed.

## ADDED Requirements

### Requirement: Restore Soft-Deleted User Endpoint
The system SHALL provide a POST endpoint at `/api/admin/users/{id}/restore` for restoring soft-deleted users. Only users with `is_admin = true` SHALL access this endpoint. The restore operation sets `is_deleted = false` and updates the `updated_at` timestamp.

#### Scenario: Successful restore
- **WHEN** an admin POSTs to /api/admin/users/{id}/restore for a soft-deleted user
- **THEN** the system sets `is_deleted = false`, updates `updated_at`, and responds with 200 with the restored user data

#### Scenario: Restore active user returns conflict
- **WHEN** an admin POSTs to /api/admin/users/{id}/restore for a user who is not soft-deleted
- **THEN** the system returns 409 Conflict with message "User is not deleted"

#### Scenario: Restore non-existent user
- **WHEN** an admin POSTs to /api/admin/users/{id}/restore for an ID that does not exist
- **THEN** the system returns 404 Not Found

### Requirement: Admin-Only Access Control
**Reason:** Access is now determined by `is_admin` flag, not ADMIN role.

All user management endpoints SHALL be accessible only to users with the `is_admin = true` flag. Access MUST be enforced at two levels: (1) SecurityConfig URL pattern matching on `/api/admin/**`, and (2) @PreAuthorize("hasRole('ADMIN')") annotation on the controller class. The JwtCookieAuthenticationFilter SHALL add ROLE_ADMIN authority to the Authentication when the user's `isAdmin = true`. Non-admin users attempting access SHALL receive 403 Forbidden.

#### Scenario: Admin (GUEST + isAdmin=true) accesses user management
- **WHEN** an authenticated user with role=GUEST and isAdmin=true GETs /api/admin/users
- **THEN** the request proceeds normally (has ROLE_ADMIN authority)

#### Scenario: Editor without isAdmin denied access
- **WHEN** an authenticated user with role=EDITOR and isAdmin=false GETs /api/admin/users
- **THEN** the system responds with 403 Forbidden

#### Scenario: Admin (EDITOR + isAdmin=true) accesses user management
- **WHEN** an authenticated user with role=EDITOR and isAdmin=true GETS /api/admin/users
- **THEN** the request proceeds normally

### Requirement: User Response DTO Includes isAdmin and isDeleted
The UserResponse DTO SHALL include `isAdmin` (boolean) and `isDeleted` (boolean) fields in all user response payloads. These fields SHALL be serialized in JSON responses for all user management endpoints (list, get by ID, create, update, restore).

#### Scenario: Create user response includes isAdmin and isDeleted
- **WHEN** a user is created via POST /api/admin/users
- **THEN** the 201 response includes isAdmin=false and isDeleted=false by default

#### Scenario: Soft-deleted user response shows isDeleted
- **WHEN** a soft-deleted user is retrieved via GET /api/admin/users/{id}
- **THEN** the response includes isDeleted=true and isAdmin value

#### Scenario: Restore user response shows isDeleted as false
- **WHEN** a user is restored via POST /api/admin/users/{id}/restore
- **THEN** the 200 response includes isDeleted=false

### Requirement: Role Validation
The system SHALL validate that the role field accepts only GUEST, READER, or EDITOR values. The ADMIN value SHALL be rejected in all CRUD operations (Create, Update). Validation SHALL occur at the DTO level and return 400 Bad Request for invalid role values.

#### Scenario: Valid role values accepted
- **WHEN** an admin creates a user with role=GUEST, READER, or EDITOR
- **THEN** the request is processed successfully

#### Scenario: ADMIN role rejected
- **WHEN** an admin creates a user with role=ADMIN
- **THEN** the system responds with 400 Bad Request
