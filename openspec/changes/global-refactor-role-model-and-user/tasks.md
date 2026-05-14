## 1. Liquibase Schema Migration

- [ ] 1.1 Написать Liquibase-миграцию: добавить колонки `is_admin` BOOLEAN NOT NULL DEFAULT FALSE и `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE в таблицу `users`
- [ ] 1.2 В той же миграции: обновить CHECK constraint `chk_users_role` на ('Guest', 'Reader', 'Editor')
- [ ] 1.3 Добавить частичный индекс `idx_users_not_deleted` на `(id) WHERE is_deleted = false`
- [ ] 1.4 Пересобрать БД, проверить что схема применяется без ошибок

## 2. Domain Layer Changes

- [ ] 2.1 Обновить `GlobalRole.java`: заменить ADMIN на GUEST, dbValue "Admin"→"Guest". Добавить fallback в `fromDbValue()` для "Admin"→EDITOR (обратная совместимость JWT)
- [ ] 2.2 Обновить `User.java`: добавить поля `isAdmin` (boolean), `isDeleted` (boolean). Обновить фабричные методы `create()` и `restore()`
- [ ] 2.3 Добавить в `User.java` методы домена: `softDelete()`, `restore()`, `isDeleted()`, `isActive()`, `isAdmin()`
- [ ] 2.4 Переопределить `isAdmin()` в User — проверять поле `isAdmin` вместо роли
- [ ] 2.5 Обновить `UserRepository.java`: добавить методы `findByIdIncludingDeleted()`, `findAllActive()`, `findAllIncludingDeleted()`, `countActive()`, `existsByLoginIncludingDeleted()`, `existsByEmailIncludingDeleted()`

## 3. Infrastructure Layer Changes

- [ ] 3.1 Обновить `UserJpaEntity.java`: добавить поля `isAdmin` и `isDeleted` с JPA-аннотациями
- [ ] 3.2 Обновить `UserJpaMapper.java`: маппинг новых полей `isAdmin` и `isDeleted` между Domain↔JPA
- [ ] 3.3 Обновить `UserJpaRepository.java`: добавить JPA-queries для soft-delete фильтрации
- [ ] 3.4 Обновить `UserRepositoryImpl.java`: реализовать новые методы доменного интерфейса, изменить `findById`, `findByLogin`, `findAll`, `count` на фильтрацию по `is_deleted = false`
- [ ] 3.5 Обновить `UserRepositoryImpl.existsByLogin`/`existsByEmail` — проверять INCLUDING deleted users

## 4. JWT and Authentication Changes

- [ ] 4.1 Обновить `JwtTokenProvider.java`: добавить claim `isAdmin` в `generateToken()`, добавить геттер `isAdminFromToken()`
- [ ] 4.2 Обновить `JwtCookieAuthenticationFilter.java`: добавлять ROLE_ADMIN authority когда `user.isAdmin() = true`, маппить ROLE_GUEST/ROLE_READER/ROLE_EDITOR из GlobalRole
- [ ] 4.3 Обновить `AuthService.authenticate()`: выбрасывать `InvalidCredentialsException` если `user.isDeleted() == true`

## 5. Application Layer Changes (Services)

- [ ] 5.1 Обновить `UserService.createUser()`: добавить параметр `isAdmin`, передавать в фабричный метод User
- [ ] 5.2 Обновить `UserService.updateUser()`: добавить параметр `isAdmin`, обновлять через доменный метод
- [ ] 5.3 Заменить `UserService.deleteUser()`: вместо hard-delete вызывать `user.softDelete()` и сохранять. Убрать проверки referential integrity
- [ ] 5.4 Добавить `UserService.restoreUser()`: найти deleted-пользователя, вызвать `user.restore()`, сохранить
- [ ] 5.5 Обновить `UserService.getAllUsers()`: по умолчанию возвращать активных, добавить параметр `includeDeleted`

## 6. Interface Layer Changes (DTOs)

- [ ] 6.1 Обновить `UserResponse.java`: добавить поля `isAdmin` и `isDeleted`
- [ ] 6.2 Обновить `CreateUserRequest.java`: добавить поле `isAdmin` с валидацией, обновить allowableValues для role
- [ ] 6.3 Обновить `UpdateUserRequest.java`: добавить поле `isAdmin`
- [ ] 6.4 Обновить `RestDtoMapper.java`: маппинг `isAdmin` и `isDeleted` в UserResponse
- [ ] 6.5 Обновить OpenAPI аннотации: role allowableValues изменить на GUEST/READER/EDITOR

## 7. REST Controller Changes

- [ ] 7.1 Обновить `AdminUserController.deleteUser()`: менять поведение на soft-delete, возвращать 200 с данными пользователя вместо 204
- [ ] 7.2 Добавить `AdminUserController.restoreUser()`: POST /api/admin/users/{id}/restore
- [ ] 7.3 Обновить `AdminUserController.createUser()`: передавать `isAdmin` из request
- [ ] 7.4 Обновить `AdminUserController.updateUser()`: передавать `isAdmin` из request
- [ ] 7.5 Добавить query параметр `includeDeleted` в `getAllUsers()`
- [ ] 7.6 Обновить `AdminUserController.getUserById()`: разрешить получение deleted-пользователей

## 8. Security Configuration

- [ ] 8.1 Обновить `SecurityConfig.java`: оставить `.hasRole("ADMIN")` — теперь работает через JwtCookieAuthenticationFilter, который добавляет ROLE_ADMIN при isAdmin=true

## 9. Frontend (Thymeleaf + JS) Changes

- [ ] 9.1 Обновить `admin-users.html`: таблица — отображать isAdmin отдельной колонкой/бейджем, статус Active/Deleted вместо роли Admin
- [ ] 9.2 Обновить `admin-users.html`: replace role checkboxes — GUEST/READER/EDITOR вместо ADMIN/EDITOR/READER
- [ ] 9.3 Обновить `admin-users.html`: добавить чекбокс "isAdmin" в форму создания/редактирования
- [ ] 9.4 Обновить `admin-users.html`: для deleted-пользователей показывать кнопку "Восстановить" вместо "Удалить"
- [ ] 9.5 Обновить `admin-users.html`: фильтр по статусу (Active/Deleted)
- [ ] 9.6 Обновить `admin-users.html` JS: обработчик кнопки Restore → POST /api/admin/users/{id}/restore
- [ ] 9.7 Обновить `admin-users.html` JS: update fetch to pass includeDeleted parameter
- [ ] 9.8 Обновить `layout.html`: проверка `currentUser.admin` должна работать с новым isAdmin claim

## 10. Tests

- [ ] 10.1 Обновить интеграционные тесты авторизации: проверить JWT с isAdmin claim
- [ ] 10.2 Добавить тесты soft-delete и restore в `AdminUserIntegrationTest`
- [ ] 10.3 Обновить тесты создания/обновления пользователя с новым набором ролей
- [ ] 10.4 Добавить тест аутентификации soft-deleted пользователя (должна быть заблокирована)
- [ ] 10.5 Обновить E2E тесты `admin-users.spec.js` для нового UI

## 11. Documentation and Cleanup

- [ ] 11.1 Обновить `BACKTRACKER.md` — актуализировать затронутые эндпоинты и статусы
- [ ] 11.2 Обновить `openspec/feature-registry.json` — обновить статус и metadata feature `global-refactor-role-model-and-user`
- [ ] 11.3 Проверка критериев качества по `openspec/quality-gates.md`
