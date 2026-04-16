## Context

The Knowledge Base application requires administrative user management after authentication is established via the `auth-jwt-cookie` change. Admins need full CRUD operations on users with proper security, data integrity checks, and event-driven architecture support.

**Architecture**: Clean/Hexagonal — 4 layers:
- `interfaces/rest/controller/AdminUserController` — REST endpoint handling
- `interfaces/rest/dto/request/*` — Request DTOs with validation
- `application/service/UserService` — Business logic orchestration
- `domain/model/User` — Domain entity with factory methods and business rules (`create()`, `updateProfile()`, `updatePasswordHash()`)
- `domain/repository/UserRepository` — Repository interface (no JPA dependencies in domain)
- `domain/event/UserCreatedEvent` — Domain event for post-creation notifications

**Constraints**:
- Depends on `auth-jwt-cookie` change (admin routes configured via SecurityConfig)
- BCrypt password hashing via existing `PasswordEncoder` bean
- Existing PostgreSQL schema with `users` table
- ON DELETE RESTRICT enforced at DB and application level
- Role encoded in JWT — changes take effect only after re-authentication
- DTOs use Java records with `jakarta.validation` annotations

## Goals / Non-Goals

**Goals:**
- Full CRUD lifecycle: create, read, update, delete users
- Separate password management via dedicated endpoint
- Data integrity via ON DELETE RESTRICT checks (documents, spaces, versions)
- Validation at DTO level (login format, email format, password length)
- Pagination and sorting for user listing
- Domain event publication for extensibility (email notifications, audit)
- Admin-only access via dual-layer security (SecurityConfig + @PreAuthorize)

**Non-Goals:**
- User self-registration (users created by admin only)
- User profile self-editing (not in scope — admin-only)
- Soft delete (physical delete only)
- Bulk user import/export
- Audit logging / change history (user events available but no logging implemented)

## Decisions

### Decision 1: Separate Password Change Endpoint

**Chosen**: `PUT /api/admin/users/{id}/password` — dedicated endpoint separate from profile update

**Rationale**:
- Password is sensitive data — separate endpoint with separate validation rules
- Update profile endpoint returns user data (400/404/409 errors); password endpoint returns 204 (no body) — cleaner response semantics
- Prevents accidental password exposure in profile update responses
- Simpler security model: one endpoint = one concern

**Trade-offs**:
- Extra API call if admin needs to update profile and password simultaneously
- Mitigated: in practice, these are separate admin workflows

### Decision 2: Physical Delete with Application-Level RESTRICT Checks

**Chosen**: Explicit repository methods `hasDocuments()`, `hasOwnedSpaces()`, `hasVersions()` before deletion, throwing `ConflictException` with descriptive messages

**Rationale**:
- DB-level ON DELETE RESTRICT would throw a generic SQL exception — not user-friendly
- Application-level checks allow custom error messages: "Сначала удалите или переназначьте документы"
- Three separate checks provide specific guidance per referential constraint
- Physical delete (not soft) keeps database clean

**Trade-offs**:
- Three extra DB queries on delete (acceptable for admin-only operation)
- Race condition possible (another doc created between check and delete) — acceptable for admin-only CRUD, could add transaction-level lock if needed

### Decision 3: Partial Update with Nullable Fields

**Chosen**: `UpdateUserRequest` with all fields optional (nullable). Null = no change.

**Rationale**:
- Admins often need to change just one field (e.g., reset role without touching login)
- No need for PATCH semantics — PUT with partial body is simpler in Spring
- Domain method `updateProfile(login, email, role)` already handles null checks internally

**Trade-offs**:
- PUT is not idempotent for partial updates (semantically PATCH, but simpler in practice)
- Client must know which fields to include vs omit — clear via Swagger docs

### Decision 4: Role Change Does Not Force JWT Reissue

**Chosen**: Role changes take effect on next login only — no automatic JWT invalidation

**Rationale**:
- JWT is stateless — cannot revoke individual tokens without a token blocklist
- Implementing token invalidation requires Redis or DB-based token registry — adds complexity
- 24h max window of inconsistent permissions is acceptable for admin operations
- Documented behavior: admin instructions include "user must re-login for role changes to take effect"

**Trade-offs**:
- Brief inconsistency: user may have Editor permissions but still carry Reader JWT role
- Could be mitigated in future with a token blocklist or shorter JWT expiration

### Decision 5: UserCreatedEvent for Post-Creation Extensibility

**Chosen**: Publish `UserCreatedEvent` via Spring's `ApplicationEventPublisher` after successful user creation

**Rationale**:
- Decouples user creation from downstream concerns (email notifications, audit logs, welcome messages)
- Standard Spring pattern — no additional dependencies
- Event listeners can be added later without modifying `UserService`

**Trade-offs**:
- Event delivery is synchronous (in same thread) — could slow response time with slow listeners
- Could be made async with `@Async` in future if needed

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| Role in JWT stale after role change | Low | 24h max; documented; admin can ask user to re-login |
| Race condition on user deletion | Very Low | Admin-only operation; unlikely to have concurrent doc creation |
| Synchronous event blocks response | Low | No listeners implemented yet; can make async with `@Async` |
| ConflictException messages in Russian | Low | Acceptable for Russian-speaking admin UI; can i18n later |
| Password sent in plaintext over network | Medium | HTTPS required in production; Secure cookie flag must be enabled |
