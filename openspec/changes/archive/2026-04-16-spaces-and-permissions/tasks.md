## 1. Domain Layer — Space and Permission Models

- [x] 1.1 Implement `Space` domain entity with factory methods `create()`, `restore()`, and business methods `updateDescription()`, `transferOwnership()`
- [x] 1.2 Implement `SpacePermission` domain entity with factory methods `grant()`, `restore()`, auto-setting `grantedAt` timestamp
- [x] 1.3 Implement `PermissionType` enum with values READ, WRITE, OWNER and methods `canWrite()`, `canRead()`
- [x] 1.4 Implement `SpaceRepository` interface with save, findById, findByName, findAll, findByOwnerId, deleteById, existsByName, count methods
- [x] 1.5 Implement `SpacePermissionRepository` interface with save, findById, findBySpaceIdAndUserId, findByUserId, findBySpaceId, existsBySpaceIdAndUserIdAndPermissionType, hasWriteAccess, hasReadAccess, deleteById, deleteBySpaceIdAndUserId methods

## 2. Application Service Layer

- [x] 2.1 Implement `SpaceService.createSpace()` — existence check for owner, name uniqueness, domain entity creation, auto-grant OWNER permission, save both
- [x] 2.2 Implement `SpaceService.getAllSpaces()` — paginated list of all spaces (ADMIN view)
- [x] 2.3 Implement `SpaceService.getSpacesForUser()` — admin gets all spaces, non-admin gets only spaces with permission entries
- [x] 2.4 Implement `SpaceService.getSpaceById()` — space lookup with SpaceNotFoundException
- [x] 2.5 Implement `SpaceService.grantPermission()` — existence checks for space and user, duplicate prevention, permission creation
- [x] 2.6 Implement `SpaceService.getUserPermissionsInSpace()` — all permission types for a user in a specific space
- [x] 2.7 Implement `PermissionService.canWrite()` — ADMIN always true, READER always false, EDITOR checks hasWriteAccess in repository
- [x] 2.8 Implement `PermissionService.canRead()` — ADMIN always true, others check hasReadAccess in repository
- [x] 2.9 Implement `PermissionService.getUserPermissions()` — ADMIN gets [READ, WRITE, OWNER], others get actual permissions from DB
- [x] 2.10 Implement `PermissionService.getPermissionFlags()` — return PermissionFlags(canRead, canEdit, canCreate) based on computed permissions

## 3. Request DTOs with Validation

- [x] 3.1 Create `CreateSpaceRequest` record with validation: @NotBlank name (2-200 chars), optional description, nullable ownerId
- [x] 3.2 Create `GrantPermissionRequest` record with validation: @NotNull userId, @NotNull permissionType (READ/WRITE/OWNER)

## 4. Response DTOs and Mappers

- [x] 4.1 Create `SpaceResponse` DTO (id, name, description, ownerId, createdAt, updatedAt)
- [x] 4.2 Create `SpacePermissionResponse` DTO (id, spaceId, userId, permissionType, grantedAt)
- [x] 4.3 Create `UserPermissionsResponse` DTO (spaceId, permissions list, canRead, canEdit, canCreate)
- [x] 4.4 Ensure `RestDtoMapper` maps Space → SpaceResponse, SpacePermission → SpacePermissionResponse

## 5. Space Controller (Admin + User Endpoints)

- [x] 5.1 Implement `GET /api/admin/spaces` — list all spaces with pagination (ADMIN only via @PreAuthorize)
- [x] 5.2 Implement `POST /api/admin/spaces` — create space with optional ownerId fallback to current user (ADMIN only)
- [x] 5.3 Implement `POST /api/admin/spaces/{spaceId}/permissions` — grant permission to user on space (ADMIN only)
- [x] 5.4 Implement `GET /api/spaces` — list spaces accessible to current user (admin sees all, others see permitted)

## 6. Permission Controller (User-Facing Endpoints)

- [x] 6.1 Implement `GET /api/user/permissions?spaceId={id}` — permission list + UI flags for current user in space
- [x] 6.2 Implement `GET /api/user/spaces` — all spaces user has access to (duplicate of SpaceController but via PermissionService)

## 7. Swagger/OpenAPI Documentation

- [x] 7.1 Add `@Tag` annotations to `SpaceController` ("Spaces") and `PermissionController` ("User Permissions")
- [x] 7.2 Add `@Operation` and `@ApiResponses` to all endpoints
- [x] 7.3 Add `@Schema` annotations to all request and response DTOs

## 8. Error Handling and Exceptions

- [x] 8.1 Implement `SpaceNotFoundException` for missing space lookups
- [x] 8.2 Implement `ConflictException` for duplicate space names and duplicate permissions
- [x] 8.3 Ensure proper JSON error responses for validation (400), not found (404), conflict (409)

## 9. Quality Gates

- [x] 9.1 Run all quality gates as defined in `openspec/quality-gates.md`
- [x] 9.2 Verify ADMIN bypass works correctly (no SpacePermissions needed for access)
- [x] 9.3 Verify auto-grant OWNER permission is created on space creation
- [x] 9.4 Verify permission flags match actual RBAC rules (canEdit=false for READER, etc.)
- [x] 9.5 Verify space name uniqueness enforced at both application and database level

## Out of Scope

- Automated unit/integration tests (separate team member responsibility)