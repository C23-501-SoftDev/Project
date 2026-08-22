## Why

Group management (US4.1.8), group membership (US4.1.9) and group-level space permissions (US4.2.2) exist only as REST endpoints. An administrator cannot use them from the product: the admin panel has pages for Users and Spaces, but nothing for Groups, and the space permissions modal can grant rights to individual users only. Acceptance of these stories currently requires Swagger UI, which is not an acceptable hand-off to a non-technical reviewer.

## What Changes

- Add an admin page `GET /admin/groups` with a group table (id, name, description, member count) and pagination.
- Add create/edit/delete of groups from that page, reusing the existing modal + toast + confirmation patterns.
- Add a member management modal: list members of a group, add a user, remove a user (US4.1.9).
- Extend the existing space permissions modal so a permission can be granted to **either a user or a group**, and list both kinds of grants with individual revoke buttons (US4.2.2).
- Add a "Группы" tab to the admin sidebar between Spaces and Settings.

## Impact

- **Frontend**: New template `templates/pages/admin-groups.html`. Modified `templates/admin-layout.html` (sidebar tab) and `templates/pages/admin-spaces.html` (subject selector + combined permission list).
- **Backend**: `PageController` gains one SSR route (`/admin/groups`). No new REST endpoints — the page consumes the existing `/api/admin/groups**` and `/api/admin/spaces/{id}/group-permissions` API.
- **Security**: The route is under `/admin/**`, already restricted to `ROLE_ADMIN` by `SecurityConfig`; no security configuration changes.
- **API**: No endpoint added, removed or changed.
