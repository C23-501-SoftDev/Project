## ADDED Requirements

### Requirement: Three-Level Space Permission Hierarchy
The system SHALL define three permission types for space access: READ (view documents), WRITE (create/edit documents, includes READ), and OWNER (full control of space, includes READ and WRITE). PermissionTypes form a hierarchy: READ < WRITE < OWNER. The enum MUST provide `canWrite()` (true for WRITE and OWNER) and `canRead()` (true for all types).

#### Scenario: READ permission allows viewing only
- **WHEN** a user has only READ permission on a space
- **THEN** canRead() returns true and canWrite() returns false

#### Scenario: WRITE permission allows reading and writing
- **WHEN** a user has WRITE permission on a space
- **THEN** both canRead() and canWrite() return true

#### Scenario: OWNER permission implies full control
- **WHEN** a user has OWNER permission on a space
- **THEN** both canRead() and canWrite() return true

### Requirement: TWO-level RBAC (Global Role + Space Permission)
The system SHALL enforce a two-level RBAC model where access is determined by:
- Level 1: GlobalRole (ADMIN, EDITOR, READER) — defines baseline capabilities
- Level 2: SpacePermission (READ, WRITE, OWNER) — defines which specific spaces a user can access

Access rules:
| GlobalRole | Admin panel | Create/Edit docs | Read docs |
|------------|------------|-----------------|----------|
| ADMIN      | always     | always            | always   |
| EDITOR     | denied     | needs WRITE/OWNER in space | needs any permission in space |
| READER     | denied     | never             | needs any permission in space |

The RBAC logic MUST be implemented in `PermissionService` with methods `canWrite(userId, role, spaceId)` and `canRead(userId, role, spaceId)`.

#### Scenario: ADMIN can write to any space without space permission
- **WHEN** an Admin user calls canWrite(5, ADMIN, spaceId=999) even with no SpacePermission record
- **THEN** the method returns true because ADMIN has implicit access to all resources

#### Scenario: ADMIN can read from any space without space permission
- **WHEN** an Admin user calls canRead(5, ADMIN, spaceId=999) even with no SpacePermission record
- **THEN** the method returns true because ADMIN has implicit access to all resources

#### Scenario: EDITOR without space permission cannot write
- **WHEN** an Editor user calls canWrite(5, EDITOR, spaceId=999) with no SpacePermission record
- **THEN** the method returns false because EDITOR needs WRITE or OWNER in the specific space

#### Scenario: EDITOR with READ permission cannot write
- **WHEN** an Editor user calls canWrite(5, EDITOR, spaceId=10) with only READ permission
- **THEN** the method returns false because READ does not grant write access

#### Scenario: EDITOR with WRITE permission can write
- **WHEN** an Editor user calls canWrite(5, EDITOR, spaceId=10) with WRITE permission in space 10
- **THEN** the method returns true

#### Scenario: EDITOR with OWNER permission can write
- **WHEN** an Editor user calls canWrite(5, EDITOR, spaceId=10) with OWNER permission in space 10
- **THEN** the method returns true

#### Scenario: READER cannot write regardless of space permission
- **WHEN** a Reader user calls canWrite(5, READER, spaceId=10) even with OWNER permission
- **THEN** the method returns false because READER global role never allows write operations

#### Scenario: READER with READ permission can read
- **WHEN** a Reader user calls canRead(5, READER, spaceId=10) with READ permission
- **THEN** the method returns true

#### Scenario: User with no space permission cannot read
- **WHEN** an Editor user calls canRead(5, EDITOR, spaceId=999) with no SpacePermission record
- **THEN** the method returns false because non-ADMIN users need explicit space access

### Requirement: Grant Space Permission
The system SHALL allow an administrator to grant a space permission (READ, WRITE, or OWNER) to a user on a specific space. The system MUST validate that the space exists, the user exists, and the same permission type does not already exist for that user-space combination (UNIQUE constraint).

#### Scenario: Successful permission grant
- **WHEN** an admin POSTs to /api/admin/spaces/{spaceId}/permissions with userId=3 and permissionType=WRITE
- **THEN** the system creates the permission record and responds with 201 and the permission data

#### Scenario: Duplicate permission rejected
- **WHEN** an admin attempts to grant WRITE permission to a user who already has WRITE on that space
- **THEN** the system responds with 409 Conflict

#### Scenario: Grant permission for non-existent space
- **WHEN** an admin POSTs with a spaceId that does not exist
- **THEN** the system responds with 404 Not Found

#### Scenario: Grant permission for non-existent user
- **WHEN** an admin POSTs with a userId that does not exist
- **THEN** the system responds with 404 Not Found

### Requirement: Permission Flags for UI
The system SHALL provide a `PermissionFlags` response containing boolean fields `canRead`, `canEdit`, and `canCreate`. The flags MUST be computed based on the two-level RBAC: ADMIN gets all true; EDITOR/READER get flags based on their space permissions. `canCreate` SHALL equal `canWrite` (same condition). The flags endpoint GET /api/user/permissions?spaceId={id} MUST return both the list of PermissionTypes AND the PermissionFlags.

#### Scenario: ADMIN gets all permission flags
- **WHEN** an Admin user GETs /api/user/permissions?spaceId=10
- **THEN** the response contains canRead=true, canEdit=true, canCreate=true and permissions list is [READ, WRITE, OWNER]

#### Scenario: EDITOR with WRITE gets canEdit and canCreate
- **WHEN** an Editor user with WRITE permission on space 10 GETs /api/user/permissions?spaceId=10
- **THEN** the response contains canRead=true, canEdit=true, canCreate=true and permissions from space_permissions

#### Scenario: EDITOR with READ gets only canRead
- **WHEN** an Editor user with READ permission on space 10 GETs /api/user/permissions?spaceId=10
- **THEN** the response contains canRead=true, canEdit=false, canCreate=false

#### Scenario: READER with READ gets only canRead
- **WHEN** a Reader user with READ permission on space 10 GETs /api/user/permissions?spaceId=10
- **THEN** the response contains canRead=true, canEdit=false, canCreate=false

#### Scenario: User with no permission gets all false
- **WHEN** an Editor user with no permission on space 10 GETs /api/user/permissions?spaceId=10
- **THEN** the response contains canRead=false, canEdit=false, canCreate=false and empty permissions list

### Requirement: Space Permission Domain Model
The system SHALL represent a space permission as a domain entity with fields: id, spaceId, userId, permissionType (enum READ/WRITE/OWNER), grantedAt (timestamp). The entity MUST be constructed via a factory method `grant(spaceId, userId, permissionType)` and restored from storage via `restore()`. The grantedAt timestamp MUST be set automatically upon creation.

#### Scenario: Factory method sets grantedAt timestamp
- **WHEN** a permission is created via `SpacePermission.grant(10, 5, PermissionType.WRITE)`
- **THEN** grantedAt is set to the current time

### Requirement: SpacePermission Domain Repository
The system SHALL provide a `SpacePermissionRepository` interface with methods for: save, findById, findBySpaceIdAndUserId (single space), findByUserId (all spaces), findBySpaceId (all users in a space), existsBySpaceIdAndUserIdAndPermissionType (uniqueness check), hasWriteAccess (checks WRITE or OWNER), hasReadAccess (checks any permission), deleteById, and deleteBySpaceIdAndUserId (bulk revoke).

#### Scenario: hasWriteAccess returns true for WRITE permission
- **WHEN** a user has a WRITE permission record for space 10
- **THEN** `hasWriteAccess(10, userId)` returns true

#### Scenario: hasWriteAccess returns true for OWNER permission
- **WHEN** a user has an OWNER permission record for space 10
- **THEN** `hasWriteAccess(10, userId)` returns true

#### Scenario: hasReadAccess returns true for any permission
- **WHEN** a user has at least one permission record (READ, WRITE, or OWNER) for space 10
- **THEN** `hasReadAccess(10, userId)` returns true

#### Scenario: hasReadAccess returns false for no permission
- **WHEN** a user has no permission records for space 10
- **THEN** `hasReadAccess(10, userId)` returns false
