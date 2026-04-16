## ADDED Requirements

### Requirement: Create Space
The system SHALL allow an administrator to create a new document space by providing a unique name and description, along with an optional owner ID. If the owner ID is not specified, the currently authenticated user SHALL become the owner automatically. Upon creation, the system MUST automatically grant OWNER permission to the owner via a `SpacePermission` record. Space names MUST be unique across the system.

#### Scenario: Successful space creation with explicit owner
- **WHEN** an admin POSTs to /api/admin/spaces with name "Engineering Docs", description "Team documentation", and ownerId=5
- **THEN** the system creates the space, automatically grants OWNER permission to userId 5, and responds with 201 and the space data

#### Scenario: Successful space creation with default owner
- **WHEN** an admin POSTs to /api/admin/spaces with name "Engineering Docs" but no ownerId
- **THEN** the system assigns the currently authenticated user as the owner and creates the space with 201

#### Scenario: Duplicate space name rejected
- **WHEN** an admin attempts to create a space with a name that already exists
- **THEN** the system responds with 409 Conflict and an error message specifying the name is taken

### Requirement: List All Spaces (Admin)
The system SHALL allow an administrator to retrieve all spaces in the system with pagination. The endpoint MUST be restricted to ADMIN role only.

#### Scenario: Admin lists all spaces
- **WHEN** an authenticated Admin GETs /api/admin/spaces
- **THEN** the system returns paginated list of all spaces sorted by default page size 50

#### Scenario: Non-admin denied access to all spaces list
- **WHEN** an authenticated Editor or Reader GETs /api/admin/spaces
- **THEN** the system responds with 403 Forbidden

### Requirement: List User's Accessible Spaces
The system SHALL return only the spaces that the currently authenticated user has access to. ADMIN users SHALL see all spaces. EDITOR and READER users SHALL see only spaces where they have at least one entry in `space_permissions` (any PermissionType: READ, WRITE, or OWNER).

#### Scenario: Admin sees all spaces
- **WHEN** an Admin user GETs /api/spaces or /api/user/spaces
- **THEN** the system returns all spaces in the system

#### Scenario: Editor sees only permitted spaces
- **WHEN** an Editor user with WRITE permission on space "Engineering" but no permissions on "Finance" GETs /api/spaces
- **THEN** the system returns only the "Engineering" space (and any other spaces where the user has a permission entry)

#### Scenario: Reader sees only permitted spaces
- **WHEN** a Reader user with READ permission on "Public Docs" GETs /api/spaces
- **THEN** the system returns only the "Public Docs" space

#### Scenario: User with no permissions sees empty list
- **WHEN** an Editor user with no space_permissions entries GETs /api/spaces
- **THEN** the system returns an empty list

### Requirement: Space Domain Model
The system SHALL represent a space as a domain entity with fields: id, name (unique), description, ownerId, createdAt, updatedAt. The entity MUST be constructed via a factory method `create(name, description, ownerId)` and restored from storage via `restore()`. The entity MUST support `updateDescription()` and `transferOwnership(newOwnerId)` domain operations.

#### Scenario: Factory method sets timestamps
- **WHEN** a space is created via `Space.create("Name", "Desc", 1)`
- **THEN** createdAt and updatedAt are set to the current time

#### Scenario: Transfer ownership updates ownerId
- **WHEN** `transferOwnership(2)` is called on a space with ownerId=1
- **THEN** the ownerId changes to 2 and updatedAt is updated
