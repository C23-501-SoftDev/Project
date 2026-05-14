# user-soft-delete Specification

## Purpose
Specification for soft-delete functionality for users. Added by change global-refactor-role-model-and-user.

## Requirements

### Requirement: Soft-Delete User
The system SHALL support soft-deletion of users via the `is_deleted` boolean flag on the User entity. When an admin performs a DELETE operation on a user, the system SHALL set `is_deleted = true` instead of physically removing the user from the database. The user's `login` and `email` SHALL remain reserved (unique constraint enforced). A soft-deleted user SHALL NOT be able to authenticate. Soft-deleted users SHALL be excluded from default user lists and search results.

#### Scenario: Soft-delete sets is_deleted flag
- **WHEN** an admin DELETEs /api/admin/users/{id} for an active user
- **THEN** the system sets `is_deleted = true` for that user and responds with 200 OK with the updated user data

#### Scenario: Soft-deleted user cannot authenticate
- **WHEN** a user with `is_deleted = true` attempts to login via POST /api/auth/login
- **THEN** the system returns 401 Unauthorized with error "Account is deactivated"

#### Scenario: Soft-deleted user excluded from default list
- **WHEN** an admin GETs /api/admin/users (without includeDeleted parameter)
- **THEN** the response SHALL include only users where `is_deleted = false`

#### Scenario: Soft-deleted user login/email remain reserved
- **WHEN** an admin attempts to create a new user with the same login or email as a soft-deleted user
- **THEN** the system returns 409 Conflict indicating the login/email is already taken

#### Scenario: Cannot soft-delete already deleted user
- **WHEN** an admin DELETEs /api/admin/users/{id} for a user who already has `is_deleted = true`
- **THEN** the system returns 409 Conflict with message "User is already deleted"

### Requirement: Restore Soft-Deleted User
The system SHALL provide an endpoint to restore soft-deleted users. Only users with `is_admin = true` SHALL be able to restore. Restoring sets `is_deleted = false` and the user regains ability to authenticate.

#### Scenario: Restore sets is_deleted to false
- **WHEN** an admin POSTs to /api/admin/users/{id}/restore for a soft-deleted user
- **THEN** the system sets `is_deleted = false` and responds with 200 OK with the restored user data

#### Scenario: Cannot restore active user
- **WHEN** an admin POSTs to /api/admin/users/{id}/restore for a user who is not soft-deleted
- **THEN** the system returns 409 Conflict with message "User is not deleted"

#### Scenario: Restored user can authenticate
- **WHEN** a user whose `is_deleted` was set back to false attempts to login
- **THEN** the login succeeds normally (assuming correct credentials)

### Requirement: Include Deleted Users in Admin List
The admin user list endpoint SHALL support an optional query parameter `includeDeleted`. When `includeDeleted=true`, the response SHALL include both active and soft-deleted users. Each user response SHALL include the `isDeleted` field so the UI can display status appropriately.

#### Scenario: List users without deleted by default
- **WHEN** an admin GETs /api/admin/users (no includeDeleted parameter)
- **THEN** only active (`is_deleted = false`) users are returned

#### Scenario: List users with deleted when requested
- **WHEN** an admin GETs /api/admin/users?includeDeleted=true
- **THEN** both active and soft-deleted users are returned in the response

### Requirement: Repository Layer Soft-Delete Support
The UserRepository domain interface SHALL provide methods to query active users, find users including deleted ones, and restore users. The default behavior of `findById`, `findByLogin`, `findAll` SHALL exclude soft-deleted users. Dedicated methods like `findByIdIncludingDeleted`, `findAllIncludingDeleted` SHALL return all users regardless of `is_deleted` status.

#### Scenario: findByLogin excludes deleted users
- **WHEN** `userRepository.findByLogin("someLogin")` is called for a soft-deleted user
- **THEN** returns `Optional.empty()`

#### Scenario: findByIdIncludingDeleted returns deleted users
- **WHEN** `userRepository.findByIdIncludingDeleted(id)` is called for a soft-deleted user
- **THEN** returns `Optional<User>` with the deleted user's data

#### Scenario: count() counts only active users
- **WHEN** `userRepository.count()` is called
- **THEN** returns only the count of users where `is_deleted = false`
