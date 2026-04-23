## ADDED Requirements

### Requirement: Create User
The system SHALL allow an administrator to create a new user by providing login, email, password, and role. The system MUST validate login uniqueness, email uniqueness, and password strength (min 6 chars). The password MUST be hashed with BCrypt before storage. Default role is READER if null. Upon successful creation, a `UserCreatedEvent` MUST be published with userId, email, and login.

#### Scenario: Successful user creation
- **WHEN** an admin POSTs to /api/admin/users with valid login, email, password, and role
- **THEN** the system creates the user with a BCrypt-hashed password, assigns an ID, publishes UserCreatedEvent, and responds with 201 and the user data

#### Scenario: Duplicate login rejected
- **WHEN** an admin attempts to create a user with a login that already exists
- **THEN** the system responds with 409 Conflict and an error message specifying the login is taken

#### Scenario: Duplicate email rejected
- **WHEN** an admin attempts to create a user with an email that already exists
- **THEN** the system responds with 409 Conflict and an error message specifying the email is taken

#### Scenario: Invalid login format rejected
- **WHEN** an admin attempts to create a user with a login containing special characters outside [a-zA-Z0-9._-]
- **THEN** the system responds with 400 Bad Request and a validation error message

#### Scenario: Login length validation
- **WHEN** an admin attempts to create a user with a login shorter than 3 or longer than 100 characters
- **THEN** the system responds with 400 Bad Request and a validation error message

#### Scenario: Password too short
- **WHEN** an admin attempts to create a user with a password shorter than 6 characters
- **THEN** the system responds with 400 Bad Request and a validation error

#### Scenario: Missing role defaults to READER
- **WHEN** a user is created with role=null via the domain factory method
- **THEN** the system assigns GlobalRole.READER as the default role

### Requirement: Update User Profile
The system SHALL allow an administrator to update a user's login, email, and/or role. All fields are optional (null = no change). When login or email is changed, uniqueness MUST be re-validated against other users. The password MUST NOT be updated through this endpoint.

#### Scenario: Successful login update
- **WHEN** an admin PUTs to /api/admin/users/{id} with a new valid login
- **THEN** the user's login is updated and the system responds with 200 and the updated user data

#### Scenario: Successful role change
- **WHEN** an admin PUTs to /api/admin/users/{id} with a different role
- **THEN** the user's role is updated and the system responds with 200
- **THEN** the new role takes effect only after the user's next authentication (role is encoded in JWT)

#### Scenario: Update with conflict on login
- **WHEN** an admin attempts to update a user's login to one that belongs to another user
- **THEN** the system responds with 409 Conflict

#### Scenario: Update with conflict on email
- **WHEN** an admin attempts to update a user's email to one that belongs to another user
- **THEN** the system responds with 409 Conflict

#### Scenario: Partial update (only one field)
- **WHEN** an admin PUTs to /api/admin/users/{id} with only the email field (login and role are null)
- **THEN** only the email is updated; login and role remain unchanged

#### Scenario: Update non-existent user
- **WHEN** an admin PUTs to /api/admin/users/{id} with an ID that does not exist
- **THEN** the system responds with 404 Not Found

### Requirement: Change User Password
The system SHALL allow an administrator to reset a user's password via a dedicated PUT endpoint at `/api/admin/users/{id}/password`. The new password MUST meet minimum length requirements (6 characters) and MUST be hashed with BCrypt before storage. The password MUST NOT be transmitted in the update profile endpoint.

#### Scenario: Successful password change
- **WHEN** an admin PUTs to /api/admin/users/{id}/password with a valid new password
- **THEN** the system hashes the password with BCrypt, updates the user's passwordHash, and responds with 204 No Content

#### Scenario: Password too short
- **WHEN** an admin PUTs a password shorter than 6 characters
- **THEN** the system responds with 400 Bad Request and a validation error

#### Scenario: Password for non-existent user
- **WHEN** an admin PUTs a password for a userId that does not exist
- **THEN** the system responds with 404 Not Found

### Requirement: Delete User with Referential Integrity
The system SHALL physically delete a user by ID. Before deletion, the system MUST verify referential integrity by checking three conditions: the user must not be an author of any documents, must not own any spaces, and must not have created any document versions. If any condition fails, deletion MUST be rejected with a descriptive conflict error.

#### Scenario: Successful user deletion
- **WHEN** an admin DELETEs /api/admin/users/{id} for a user with no associated documents, spaces, or versions
- **THEN** the system removes the user from the database and responds with 204 No Content

#### Scenario: Cannot delete user with documents
- **WHEN** an admin attempts to delete a user who is the author of one or more documents
- **THEN** the system responds with 409 Conflict and a message instructing to reassign or delete documents first

#### Scenario: Cannot delete user with owned spaces
- **WHEN** an admin attempts to delete a user who owns one or more spaces
- **THEN** the system responds with 409 Conflict and a message instructing to delete or transfer space ownership first

#### Scenario: Cannot delete user with versions
- **WHEN** an admin attempts to delete a user who created one or more document versions
- **THEN** the system responds with 409 Conflict and a message instructing to delete related versions first

#### Scenario: Delete non-existent user
- **WHEN** an admin DELETEs /api/admin/users/{id} with an ID that does not exist
- **THEN** the system responds with 404 Not Found

### Requirement: List Users with Pagination
The system SHALL allow an administrator to retrieve all registered users paginated. The endpoint MUST support: page number (0-based, default 0), page size (default 20), sort field (default "createdAt"), and sort direction (default "desc"). The response MUST include the list of users and metadata for pagination (total count, current page, page size).

#### Scenario: First page of users sorted by creation date
- **WHEN** an admin GETs /api/admin/users (default parameters)
- **THEN** the system returns page 0 with up to 20 users sorted by createdAt descending, plus total count

#### Scenario: Custom pagination parameters
- **WHEN** an admin GETs /api/admin/users?page=1&size=10&sortBy=login&sortDir=asc
- **THEN** the system returns page 1 with 10 users sorted by login ascending

#### Scenario: Empty user list
- **WHEN** an admin GETs /api/admin/users with no users in the system
- **THEN** the system returns an empty list with total=0

### Requirement: Get User by ID
The system SHALL allow an administrator to retrieve a single user's details by ID. If the user exists, return their full data (excluding password hash). If not found, return 404.

#### Scenario: Get existing user by ID
- **WHEN** an admin GETs /api/admin/users/{id} with a valid existing ID
- **THEN** the system returns 200 with the user's data (id, login, email, role, createdAt, updatedAt)

#### Scenario: Get non-existent user
- **WHEN** an admin GETs /api/admin/users/{id} with an ID that does not exist
- **THEN** the system returns 404 Not Found

### Requirement: Admin-Only Access Control
All user management endpoints SHALL be accessible only to users with the ADMIN role. Access MUST be enforced at two levels: (1) SecurityConfig URL pattern matching on `/api/admin/**`, and (2) @PreAuthorize("hasRole('ADMIN')") annotation on the controller class. Non-admin users attempting access SHALL receive 403 Forbidden.

#### Scenario: Admin accesses user management endpoint
- **WHEN** an authenticated Admin user GETs /api/admin/users
- **THEN** the request proceeds normally

#### Scenario: Editor denied access to user management
- **WHEN** an authenticated Editor user GETs /api/admin/users
- **THEN** the system responds with 403 Forbidden

#### Scenario: Reader denied access to user management
- **WHEN** an authenticated Reader user GETs /api/admin/users
- **THEN** the system responds with 403 Forbidden
