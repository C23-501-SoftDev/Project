## ADDED Requirements

### Requirement: Admin can add a user to a group
The system MUST allow administrators to add an existing user to an existing group. After being added, the user is a member of the group and inherits the access policies associated with that group.

#### Scenario: Add user to group
- **WHEN** Admin sends POST `/api/admin/groups/{groupId}/members` with a valid `userId`
- **THEN** the system creates the membership and returns 201 Created with the membership and user data

#### Scenario: Add user to a non-existing group
- **WHEN** Admin sends POST `/api/admin/groups/{groupId}/members` for a group that does not exist
- **THEN** the system returns 404 Not Found

#### Scenario: Add a non-existing user
- **WHEN** Admin sends POST `/api/admin/groups/{groupId}/members` with a `userId` that does not exist
- **THEN** the system returns 404 Not Found

#### Scenario: Add a user who is already a member
- **WHEN** Admin adds a user who already belongs to the group
- **THEN** the system returns 409 Conflict and does not create a duplicate membership

### Requirement: Admin can remove a user from a group
The system MUST allow administrators to remove a user from a group. After removal, the user loses the access rights obtained through that group.

#### Scenario: Remove member
- **WHEN** Admin sends DELETE `/api/admin/groups/{groupId}/members/{userId}` for an existing membership
- **THEN** the system removes the membership and returns 204 No Content

#### Scenario: Remove a user who is not a member
- **WHEN** Admin sends DELETE `/api/admin/groups/{groupId}/members/{userId}` for a user who is not in the group
- **THEN** the system returns 404 Not Found

### Requirement: Admin can list group members
The system MUST allow administrators to retrieve the members of a group.

#### Scenario: List members
- **WHEN** Admin requests GET `/api/admin/groups/{groupId}/members` for an existing group
- **THEN** the system returns 200 OK with the list of members including each user's login, email and role

#### Scenario: List members of a non-existing group
- **WHEN** Admin requests GET `/api/admin/groups/{groupId}/members` for a group that does not exist
- **THEN** the system returns 404 Not Found

### Requirement: Deleting a group revokes its memberships
When a group is deleted, the system MUST remove all of its memberships while leaving the users intact.

#### Scenario: Delete group with members
- **WHEN** Admin deletes a group that has members
- **THEN** the system removes all memberships of that group and the users themselves remain in the system

### Requirement: Membership management is restricted to administrators
The system MUST restrict all group-membership endpoints to users with the ADMIN role.

#### Scenario: Non-admin access is forbidden
- **WHEN** a non-admin user calls any `/api/admin/groups/{groupId}/members` endpoint
- **THEN** the system returns 403 Forbidden
