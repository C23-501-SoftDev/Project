## Context

After authentication (`auth-jwt-cookie`) and user management (`admin-users-crud`) are established, the Knowledge Base needs document organization via spaces with role-based access control. Spaces provide logical grouping of documents and a second-level access control beyond global roles.

**Architecture**: Clean/Hexagonal — 4 layers:
- `domain/model/*` — `Space`, `SpacePermission`, `PermissionType`, `GlobalRole`
- `domain/repository/*` — `SpaceRepository`, `SpacePermissionRepository` (pure interface, no JPA)
- `application/service/*` — `SpaceService` (CRUD orchestration), `PermissionService` (RBAC logic)
- `interfaces/rest/controller/*` — `SpaceController` (admin + user endpoints), `PermissionController` (permission flags)

**Dependencies**:
- Requires `auth-jwt-cookie` (JWT for user context, admin-only route rules)
- Requires `admin-user-management` (user creation before space ownership/permission granting)
- `SpaceService` depends on both `SpaceRepository` and `SpacePermissionRepository`
- `PermissionController` depends on both `PermissionService` and `SpaceService`

**Constraints**:
- Two-level RBAC: GlobalRole + SpacePermission combined for access decisions
- Space creation auto-grants OWNER to the creator/assigned owner
- Space names are unique system-wide
- Permission duplicates prevented via repository-level exists check

## Goals / Non-Goals

**Goals:**
- Document organization via named spaces with descriptions
- Two-level RBAC: GlobalRole (ADMIN/EDITOR/READER) + SpacePermission (READ/WRITE/OWNER)
- ADMIN sees all spaces, bypasses permission checks
- Non-ADMIN users see only spaces with explicit permission entries
- Automatic OWNER grant upon space creation
- Permission grant by admin to any user-space combination
- UI permission flags (canRead, canEdit, canCreate) for frontend consumption
- Prevent duplicate permissions (same user + same space + same type)

**Non-Goals:**
- Space-level document CRUD (document management is a separate concern)
- Space transfer of ownership UI flow (domain method exists but no controller endpoint)
- Revoking permissions (domain repository supports delete but no controller endpoint)
- Nested spaces or sub-folders
- Public/read-only spaces without explicit permissions

## Decisions

### Decision 1: Two-Level RBAC Model

**Chosen**: Combined access = GlobalRole (first level) + SpacePermission (second level)

**Logic**:
```
if role == ADMIN     → access granted to everything
if role == READER    → no write ever; read needs space permission
if role == EDITOR    → write needs WRITE/OWNER in space; read needs any space permission
```

**Rationale**:
- Single-level RBAC (roles only) would allow an Editor to see ALL documents — too broad
- ACL-only model (no global roles) would require a permission entry for every admin operation — too complex
- Two-level provides coarse control (role) + fine-grained control (space permission)

**Trade-offs**:
- More complex to reason about — documented clearly in PermissionService with RBAC table
- Frontend must check both JWT role and space permissions to determine UI behavior

### Decision 2: Space Name Uniquely (not per-user or per-tenant)

**Chosen**: Space name is unique across the entire system (not per-user)

**Rationale**:
- Simple mental model — spaces are top-level organizational units
- No naming conflicts or disambiguation needed in UI
- Prevents confusion ("Which 'Engineering' space is the right one?")

**Trade-offs**:
- Larger organizations may want duplicate names with scoping by department
- Cannot be changed without a migration strategy if names become non-unique

### Decision 3: Auto-Grant OWNER on Space Creation

**Chosen**: When a space is created, immediately create a `SpacePermission` record with `OWNER` type for the creator/assigned owner

**Rationale**:
- Every space MUST have an owner for administrative purposes
- Separate step would be error-prone (create space → forget to grant permission)
- Consistent with domain invariant: a space always has an owner

**Trade-offs**:
- Two database writes per space creation (space + permission) — acceptable, atomic transaction
- Could use DB trigger — but application-level ownership is more transparent

### Decision 4: Duplicate Permission Prevention at Application Layer

**Chosen**: Check `existsBySpaceIdAndUserIdAndPermissionType` in `grantPermission()` before saving

**Rationale**:
- Application-level error with `ConflictException` provides user-friendly error message
- DB UNIQUE constraint provides backstop but throws generic SQL error

**Trade-offs**:
- Race condition possible (concurrent requests creating same permission) — acceptable for admin-only operations
- DB UNIQUE constraint should still exist for data integrity

### Decision 5: Separate Controllers for Spaces vs Permissions

**Chosen**: `SpaceController` handles space CRUD and permission granting; `PermissionController` handles querying current user's permissions

**Rationale**:
- `SpaceController` — admin-facing: create spaces, list all spaces, grant permissions
- `PermissionController` — user-facing: what are MY permissions?, what spaces can I see?
- Clear separation of "admin manages" vs "user views own"
- Reduces confusion: admin creates permissions, user checks their own

**Trade-offs**:
- Both controllers have a `GET /api/user/spaces`-like endpoint (`/api/spaces` in SpaceController, `/api/user/spaces` in PermissionController) — they do the same thing via different services. This duplication exists because both controllers expose user-facing endpoints.

### Decision 6: ownerId Nullable in CreateSpaceRequest

**Chosen**: If `ownerId` is null in the request, use the currently authenticated user

**Rationale**:
- Admin creating a space for themselves — no need to specify their own ID
- Admin creating a space for another user — provide explicit ownerId
- Simplifies API for common case

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| EDITOR with OWNER permission could theoretically manage other users' permissions (no endpoint exists yet) | Low | Domain model supports it, no endpoint to do it |
| Space name uniqueness prevents parallel teams from using same name | Medium | Acceptable for small/medium org; can add tenant scoping later |
| Permission checks via `getSpacesForUser` loads all permissions then fetches spaces one-by-one (N+1 query) | Performance | Fine for small number of spaces; refactor to batch query with JOIN if needed |
| No endpoint for revoking permissions | Low | Repository method exists; add controller endpoint when needed |
| No endpoint for space deletion | Low | Domain supports `deleteById`; add with document migration check |
