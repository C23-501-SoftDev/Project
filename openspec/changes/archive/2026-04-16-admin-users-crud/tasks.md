## 1. Domain Layer

- [x] 1.1 Implement `User` domain entity with factory methods `create()` and `restore()`, business methods `updateProfile()` and `updatePasswordHash()`, and role-checking methods `isAdmin()`, `isEditor()`
- [x] 1.2 Implement `UserCreatedEvent` extending `DomainEvent` with userId, email, and login fields
- [x] 1.3 Implement `UserRepository` interface with CRUD methods, pagination, uniqueness checks, and referential integrity checks (`hasDocuments`, `hasOwnedSpaces`, `hasVersions`)

## 2. Application Service Layer

- [x] 2.1 Implement `UserService.createUser()` — uniqueness validation, BCrypt hashing, domain entity creation, save, publish `UserCreatedEvent`
- [x] 2.2 Implement `UserService.updateUser()` — existence check, uniqueness re-validation on changed fields, domain `updateProfile()`, save
- [x] 2.3 Implement `UserService.changePassword()` — existence check, BCrypt hash, domain `updatePasswordHash()`, save
- [x] 2.4 Implement `UserService.deleteUser()` — existence check, three RESTRICT validations (`hasDocuments`, `hasOwnedSpaces`, `hasVersions`), physical delete
- [x] 2.5 Implement `UserService.getUserById()` — existence check with `UserNotFoundException`
- [x] 2.6 Implement `UserService.getAllUsers()` — paginated list with configurable sort
- [x] 2.7 Implement `UserService.countUsers()` — total count for pagination

## 3. Request DTOs with Validation

- [x] 3.1 Create `CreateUserRequest` record with validation: @NotBlank login (3-100 chars, pattern `[a-zA-Z0-9._-]+`), @NotBlank email (valid format), @NotBlank password (6-100 chars), @NotNull role
- [x] 3.2 Create `UpdateUserRequest` record with optional fields: login (nullable, same validation), email (nullable, valid format), role (nullable)
- [x] 3.3 Create `ChangePasswordRequest` record with validation: @NotBlank newPassword (6-100 chars)

## 4. Response DTOs and Mappers

- [x] 4.1 Create `UserResponse` DTO (id, login, email, role, createdAt, updatedAt — no password hash)
- [x] 4.2 Create `PageResponse<T>` generic DTO for paginated results
- [x] 4.3 Ensure `RestDtoMapper` maps `User` domain entity → `UserResponse` DTO

## 5. REST Controller Layer

- [x] 5.1 Implement `AdminUserController` with `@RequestMapping("/api/admin/users")` and `@PreAuthorize("hasRole('ADMIN')")` class-level annotation
- [x] 5.2 Implement `GET /api/admin/users` — list users with pagination query params (page, size, sortBy, sortDir) and total count
- [x] 5.3 Implement `GET /api/admin/users/{id}` — single user by ID
- [x] 5.4 Implement `POST /api/admin/users` — create user with @Valid request, return 201
- [x] 5.5 Implement `PUT /api/admin/users/{id}` — partial update with nullable fields, return 200
- [x] 5.6 Implement `DELETE /api/admin/users/{id}` — physical delete with RESTRICT checks, return 204
- [x] 5.7 Implement `PUT /api/admin/users/{id}/password` — password reset, return 204

## 6. Swagger/OpenAPI Documentation

- [x] 6.1 Add `@Tag(name = "Admin: Users")` to `AdminUserController`
- [x] 6.2 Add `@Operation` and `@ApiResponses` to each endpoint with descriptions and response codes
- [x] 6.3 Add `@Schema` annotations to all request and response DTOs

## 7. Error Handling and Exceptions

- [x] 7.1 Implement `ConflictException` for duplicate login/email and referential integrity violations
- [x] 7.2 Implement `UserNotFoundException` for missing user lookups
- [x] 7.3 Ensure `@RestControllerAdvice` returns proper JSON error responses for 400 (validation), 404 (not found), 409 (conflict)

## 8. Quality Gates

- [x] 8.1 Run all quality gates as defined in `openspec/quality-gates.md`
- [x] 8.2 Verify all admin endpoints return 403 for non-ADMIN authenticated users
- [x] 8.3 Verify password is never included in response DTO (no password hash exposure)
- [x] 8.4 Verify UserCreatedEvent is published on successful creation

## Out of Scope
- Automated unit/integration tests (separate team member responsibility)
