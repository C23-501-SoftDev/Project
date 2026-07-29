## 1. Routing and Navigation

- [x] 1.1 Add the `GET /admin/groups` SSR route to `PageController` (`activePage=groups`).
- [x] 1.2 Add the "Группы" tab to the admin sidebar in `admin-layout.html`.

## 2. Groups Page (US4.1.8)

- [x] 2.1 Add `templates/pages/admin-groups.html` with a `data-table` (id, name, description, member count) and pagination.
- [x] 2.2 Load groups from `GET /api/admin/groups` with page/size state.
- [x] 2.3 Add the create/edit modal wired to `POST` / `PUT /api/admin/groups`.
- [x] 2.4 Add the delete confirmation modal wired to `DELETE /api/admin/groups/{id}`, stating that memberships and rights are revoked.
- [x] 2.5 Escape all rendered values; pass only numeric ids through row `onclick`.

## 3. Membership (US4.1.9)

- [x] 3.1 Add the members modal listing `GET /api/admin/groups/{id}/members`.
- [x] 3.2 Add a user picker and wire it to `POST /api/admin/groups/{id}/members`.
- [x] 3.3 Add per-member removal via `DELETE /api/admin/groups/{id}/members/{userId}`.
- [x] 3.4 Refresh the member list and the group table (member count) after each mutation.

## 4. Group Permissions on Spaces (US4.2.2)

- [x] 4.1 Add the "Кому назначить" (user/group) selector to the space permissions modal.
- [x] 4.2 Load the group list for the picker from `GET /api/admin/groups`.
- [x] 4.3 Branch the grant handler to `/permissions` or `/group-permissions` by subject type.
- [x] 4.4 Render user and group grants in one list with per-row revoke.
- [x] 4.5 Wire group revoke to `DELETE /api/admin/group-permissions/{permId}`.

## 5. Verification

- [x] 5.1 Compile the backend (`mvn compile`).
- [x] 5.2 Run the backend test suite (no regressions).
- [x] 5.3 Update `BACKTRACKER.md` / documentation to state that groups are managed from the UI.
- [ ] 5.4 Manual UI pass: create group → add member → grant group READ on a space → verify a GUEST member gains access → revoke.
