## ADDED Requirements

### Requirement: Admin can read space details
The system MUST provide a way for administrators to retrieve detailed information about a specific space by its ID.

#### Scenario: Retrieve existing space
- **WHEN** Admin requests GET `/api/admin/spaces/{id}` for an existing space
- **THEN** system returns 200 OK with name, description, ownerId, createdAt, and updatedAt

### Requirement: Admin can update space
The system MUST allow administrators to update the name, description, and owner of an existing space.

#### Scenario: Update space details
- **WHEN** Admin sends PUT `/api/admin/spaces/{id}` with new description and ownerId
- **THEN** system updates the space, sets `updatedAt` to current time, and returns 200 OK

#### Scenario: Update with duplicate name
- **WHEN** Admin sends PUT `/api/admin/spaces/{id}` with a name that already exists in another space
- **THEN** system returns 409 Conflict

### Requirement: Automatic owner permission update
When the owner of a space is changed via the update operation, the system MUST automatically update permissions.

#### Scenario: Change owner
- **WHEN** Admin updates `ownerId` of a space from User A to User B
- **THEN** User A loses `OWNER` permission and User B is granted `OWNER` permission for that space

### Requirement: Admin can delete space
The system MUST allow administrators to delete an existing space.

#### Scenario: Delete space
- **WHEN** Admin sends DELETE `/api/admin/spaces/{id}`
- **THEN** system removes the space and all associated permissions, and returns 204 No Content
