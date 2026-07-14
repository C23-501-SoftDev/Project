## ADDED Requirements

### Requirement: Admin can create a group
The system MUST allow administrators to create a user group with a unique name.

#### Scenario: Create group with unique name
- **WHEN** Admin sends POST `/api/admin/groups` with a unique `name`
- **THEN** the system creates the group and returns 201 Created with `id`, `name`, `description`, `createdAt`, `updatedAt`

#### Scenario: Create group with duplicate name
- **WHEN** Admin sends POST `/api/admin/groups` with a `name` that already exists
- **THEN** the system returns 409 Conflict and does not create a group

#### Scenario: Create group with invalid payload
- **WHEN** Admin sends POST `/api/admin/groups` with a blank `name`
- **THEN** the system returns 400 Bad Request with field validation details

### Requirement: Admin can list groups
The system MUST allow administrators to retrieve the list of groups with pagination.

#### Scenario: List groups
- **WHEN** Admin requests GET `/api/admin/groups?page=0&size=50`
- **THEN** the system returns 200 OK with a paginated `content` array and `totalElements`

### Requirement: Admin can read group details
The system MUST allow administrators to retrieve a single group by its ID.

#### Scenario: Retrieve existing group
- **WHEN** Admin requests GET `/api/admin/groups/{groupId}` for an existing group
- **THEN** the system returns 200 OK with the group data

#### Scenario: Retrieve non-existing group
- **WHEN** Admin requests GET `/api/admin/groups/{groupId}` for a group that does not exist
- **THEN** the system returns 404 Not Found

### Requirement: Admin can update a group
The system MUST allow administrators to update the name and description of an existing group.

#### Scenario: Update group details
- **WHEN** Admin sends PUT `/api/admin/groups/{groupId}` with a new name and description
- **THEN** the system updates the group, refreshes `updatedAt`, and returns 200 OK

#### Scenario: Update with duplicate name
- **WHEN** Admin sends PUT `/api/admin/groups/{groupId}` with a name that belongs to another group
- **THEN** the system returns 409 Conflict

### Requirement: Admin can delete a group
The system MUST allow administrators to delete an existing group. Deleting a group MUST remove its memberships and group-based permissions while leaving the users themselves intact.

#### Scenario: Delete group
- **WHEN** Admin sends DELETE `/api/admin/groups/{groupId}` for an existing group
- **THEN** the system removes the group and returns 204 No Content, and the group no longer appears in the list

#### Scenario: Delete non-existing group
- **WHEN** Admin sends DELETE `/api/admin/groups/{groupId}` for a group that does not exist
- **THEN** the system returns 404 Not Found

### Requirement: Group management is restricted to administrators
The system MUST restrict all group-management endpoints to users with the ADMIN role.

#### Scenario: Non-admin access is forbidden
- **WHEN** a non-admin user calls any `/api/admin/groups` endpoint
- **THEN** the system returns 403 Forbidden
