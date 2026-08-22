## Context

The backend for groups is complete (`GroupController`, `GroupService`, `space_group_permissions`), but the admin panel exposes only Users and Spaces. The admin panel is server-rendered Thymeleaf: `admin-layout.html` provides the sidebar and loads `js/admin-common.js`, and each page is a fragment injected as `${content}` that fetches its data from the REST API on `DOMContentLoaded`.

**Existing conventions to follow** (from `admin-users.html` / `admin-spaces.html`):
- `adminFetch(url, options)` — fetch wrapper that attaches the CSRF token and shows an error toast.
- `openModal(id)` / `closeModal(id)` / `showToast(msg, type)` / `escapeHtml(str)` from `admin-common.js`.
- `window.initCustomSelects()` / `window.populateCustomSelect(wrapperId, value, text)` from `js/custom-select.js` for dropdowns.
- Table markup `table.data-table` + a `.pagination` block with prev/next buttons and a page label.

**Constraints**:
- No new REST endpoints — the UI must consume the API as it already exists.
- Visual style must be indistinguishable from the existing admin pages (same classes, same modal structure, same button variants).
- `/admin/**` is already ADMIN-only in `SecurityConfig`; the new page must not require security changes.

## Goals / Non-Goals

**Goals:**
- Full group lifecycle from the UI: list, create, rename, delete.
- Membership management from the UI: view members, add, remove.
- Grant and revoke space permissions for groups next to the existing per-user grants.

**Non-Goals:**
- Search/sort/role filters on the groups table (the user list has them; groups are expected to be few).
- Bulk membership editing (add/remove is one user at a time).
- Showing a user's groups on the Users page.
- Any change to permission resolution logic — that already accounts for group permissions.

## Decisions

### Decision 1: Separate page for groups, not a tab inside Users

**Chosen**: A dedicated `/admin/groups` page with its own sidebar entry.

**Rationale**:
- Mirrors how Spaces is modelled; keeps each page's JS state independent.
- The group table has different columns (member count) and different row actions.

**Trade-offs**:
- One more sidebar item. Acceptable — the sidebar had only three entries.

### Decision 2: Subject-type selector inside the existing permissions modal

**Chosen**: The space permissions modal gains a "Кому назначить" dropdown (Пользователю / Группе) that toggles which picker is visible; the grant handler branches to `/permissions` or `/group-permissions`.

**Rationale**:
- Group permissions belong to the same mental task ("who can access this space"), so a second modal would fragment the flow.
- The grant/revoke API shapes are symmetrical, so one form serves both.

**Trade-offs**:
- The modal holds two hidden pickers. Simpler than duplicating the modal and its list rendering.

### Decision 3: One combined permission list, loaded in parallel

**Chosen**: `loadPermissionsList()` issues both `GET /permissions` and `GET /group-permissions` via `Promise.all`, renders user rows (👤) and group rows (👥) into one list, each with its own revoke button targeting the matching endpoint.

**Rationale**:
- The administrator sees the complete access picture for a space in one place.
- Parallel requests keep the modal responsive; a failure of either list degrades to an empty section rather than breaking the modal (`.catch(() => [])`).

**Trade-offs**:
- Two requests per modal open. Both are small, indexed lookups.

### Decision 4: Group name resolved from a client-side cache, not passed through `onclick`

**Chosen**: Row actions pass only the numeric group id; `manageMembers(groupId)` looks the name up in `groupsCache` populated by the last fetch.

**Rationale**:
- Interpolating a user-controlled name into an inline `onclick` string breaks on apostrophes and is an injection vector.
- Ids are numeric and safe to interpolate.

**Trade-offs**:
- Requires keeping `groupsCache` in sync with the rendered page. It is assigned in the same function that renders.

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| Group name with quotes/HTML breaks the table or injects markup | Medium | All rendered values pass through `escapeHtml`; ids only in `onclick` |
| Deleting a group silently strips space access for its members | Medium | Confirmation modal states that memberships and space rights will be revoked |
| Permissions modal shows stale data after grant/revoke | Low | Every mutation re-runs `loadPermissionsList()` |
| Member count drifts after add/remove | Low | Membership mutations refresh both the member list and the group table |
