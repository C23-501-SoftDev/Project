## Integration tests report (backend)

Источник: `Project/backend/target/surefire-reports/TEST-*.xml`

### Итог

- **Всего тестов**: 24
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Сумма времени suites**: 11.404s

### Сводка по suites

| Suite | Tests | Time (s) | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `AuthIntegrationTest` | 4 | 0.440 | 0 | 0 | 0 |
| `SecurityIntegrationTest` | 2 | 0.462 | 0 | 0 | 0 |
| `AdminUserIntegrationTest` | 7 | 7.882 | 0 | 0 | 0 |
| `SpaceIntegrationTest` | 3 | 0.596 | 0 | 0 | 0 |
| `PermissionIntegrationTest` | 3 | 0.790 | 0 | 0 | 0 |
| `ValidationErrorIntegrationTest` | 3 | 0.301 | 0 | 0 | 0 |
| `StorageIntegrationTest` | 1 | 0.014 | 0 | 0 | 0 |
| `KnowledgeBaseApplicationTests` | 1 | 0.919 | 0 | 0 | 0 |

### Покрытие сценариев (test cases)

| Suite | Test case | Сценарий | Ожидание | Факт | Статус |
|---|---|---|---|---|---|
| `AuthIntegrationTest` | `login_success_setsJwtCookie_andReturnsUser` | `POST /api/auth/login` (валидные креды) | 200 + JWT в `Set-Cookie` | OK | PASS |
| `AuthIntegrationTest` | `login_invalidCredentials_returns401ErrorResponse` | `POST /api/auth/login` (неверный пароль) | 401 + `ErrorResponse` | OK | PASS |
| `AuthIntegrationTest` | `me_withoutJwtCookie_returns401` | `GET /api/auth/me` без JWT | 401 | OK | PASS |
| `AuthIntegrationTest` | `me_withJwtCookie_returnsCurrentUser` | `GET /api/auth/me` с JWT | 200 + текущий user | OK | PASS |
| `SecurityIntegrationTest` | `publicEndpoints_areAccessibleWithoutAuth` | `/login`, `/actuator/health` без JWT | 200 | OK | PASS |
| `SecurityIntegrationTest` | `adminApi_requiresAuthentication_andAdminRole` | `/api/admin/users` (без JWT / READER / ADMIN) | 401 → 403 → 200 | OK | PASS |
| `AdminUserIntegrationTest` | `adminUsers_crud_andPasswordFlow` | create/list/conflict/404 | 201/200/409/404 | OK | PASS |
| `AdminUserIntegrationTest` | `adminUser_updateRole_requiresReLoginForNewJwtRole` | смена роли + перелогин | роль меняется после login | OK | PASS |
| `AdminUserIntegrationTest` | `adminUser_changePassword_affectsLogin` | смена пароля | 204 → (401 старый) → (200 новый) | OK | PASS |
| `AdminUserIntegrationTest` | `adminUser_delete_userWithOwnedSpaces_returns409` | delete user with owned spaces | 409 | OK | PASS |
| `AdminUserIntegrationTest` | `adminUser_delete_userWithDocuments_returns409` | delete user with documents | 409 | OK | PASS |
| `AdminUserIntegrationTest` | `adminUser_delete_userWithVersions_returns409` | delete user with versions | 409 | OK | PASS |
| `AdminUserIntegrationTest` | `adminUser_delete_userWithoutDependencies_returns204` | delete user without deps | 204 | OK | PASS |
| `SpaceIntegrationTest` | `admin_canCreateSpace_duplicateNameReturns409_andOwnerDefaultsToCurrentUser` | create space (ownerId null) + duplicate name | 201 (owner=current) + 409 | OK | PASS |
| `SpaceIntegrationTest` | `admin_grantsPermission_andUserSeesSpaceInMySpaces` | grant READ → `/api/spaces` | space виден пользователю | OK | PASS |
| `SpaceIntegrationTest` | `grantPermission_toNonExistingUser_orSpace_returns404` | grant на несуществующие сущности | 404 | OK | PASS |
| `PermissionIntegrationTest` | `myPermissions_adminAlwaysHasAllFlags_andNonExistingSpaceReturns404` | permissions: 404 на missing space + ADMIN flags | 404; затем все флаги true | OK | PASS |
| `PermissionIntegrationTest` | `myPermissions_editorFlags_dependOnSpacePermissions` | EDITOR flags: none/READ/WRITE | флаги меняются | OK | PASS |
| `PermissionIntegrationTest` | `mySpaces_endpoint_returnsSpacesWithAnyPermission` | `/api/user/spaces` после grant | space присутствует | OK | PASS |
| `ValidationErrorIntegrationTest` | `createUser_invalidPayload_returns400_withFieldErrors` | невалидный create user | 400 + `fieldErrors` | OK | PASS |
| `ValidationErrorIntegrationTest` | `login_invalidPayload_returns400_withFieldErrors` | невалидный login payload | 400 + `fieldErrors` | OK | PASS |
| `ValidationErrorIntegrationTest` | `grantPermission_invalidPayload_returns400_withFieldErrors` | невалидный grant permission | 400 + `fieldErrors` | OK | PASS |
| `StorageIntegrationTest` | `storageIsInitialized_andHealthEndpointIsUp` | storage init + health | health=UP | OK | PASS |
| `KnowledgeBaseApplicationTests` | `contextLoads` | поднятие контекста | context loads | OK | PASS |

