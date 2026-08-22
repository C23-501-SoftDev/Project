## ADDED Requirements

### Requirement: Group management page

The admin panel SHALL provide a page for managing user groups, reachable from the admin sidebar and restricted to administrators.

#### Scenario: Administrator opens the groups page

- **WHEN** an administrator opens `/admin/groups`
- **THEN** the system SHALL render the admin layout with the "Группы" tab active and display all groups with their id, name, description and member count.

#### Scenario: Non-administrator opens the groups page

- **WHEN** a user without administrator rights requests `/admin/groups`
- **THEN** the system SHALL deny access.

#### Scenario: Administrator creates a group

- **WHEN** an administrator submits the create form with a name
- **THEN** the system SHALL create the group and refresh the table.

#### Scenario: Group name is already taken

- **WHEN** an administrator submits a name that already belongs to another group
- **THEN** the page SHALL show an error notification and SHALL NOT remove the entered data.

#### Scenario: Administrator deletes a group

- **WHEN** an administrator confirms deletion of a group
- **THEN** the system SHALL delete it and the confirmation dialog SHALL state beforehand that memberships and the group's space rights are revoked.

### Requirement: Group membership management

The groups page SHALL let an administrator view and change the members of a group.

#### Scenario: Administrator views members

- **WHEN** an administrator opens the members dialog of a group
- **THEN** the system SHALL list the current members with their login and email.

#### Scenario: Administrator adds a member

- **WHEN** an administrator picks a user and confirms
- **THEN** the system SHALL add the user to the group and refresh both the member list and the group's member count.

#### Scenario: Administrator removes a member

- **WHEN** an administrator removes a user from the group
- **THEN** the system SHALL remove the membership and refresh both the member list and the group's member count.

### Requirement: Space permissions for users and groups

The space permissions dialog SHALL allow granting a permission to either a user or a group and SHALL show both kinds of grants.

#### Scenario: Administrator grants a permission to a group

- **WHEN** an administrator selects subject type "Группе", picks a group and a permission type, and confirms
- **THEN** the system SHALL create a group permission for that space and refresh the permission list.

#### Scenario: Administrator grants a permission to a user

- **WHEN** an administrator selects subject type "Пользователю", picks a user and a permission type, and confirms
- **THEN** the system SHALL create a user permission for that space and refresh the permission list.

#### Scenario: Permission list shows both subject kinds

- **WHEN** the permissions dialog is opened for a space
- **THEN** the system SHALL list user grants and group grants together, each row visually distinguishing the subject kind and offering its own revoke action.

#### Scenario: Administrator revokes a group permission

- **WHEN** an administrator revokes a group grant
- **THEN** the system SHALL delete that group permission and refresh the list.

#### Scenario: Subject is not selected

- **WHEN** an administrator confirms the grant form without selecting a user or group
- **THEN** the page SHALL show an error notification and SHALL NOT send the request.
