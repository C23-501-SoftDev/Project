## 1. Logging Infrastructure

- [x] 1.1 Add centralized logger.
- [x] 1.2 Add `LOG_LEVEL` configuration.
- [x] 1.3 Add `LOG_FORMAT` configuration for centralized application event payloads.

## 2. Application and HTTP

- [x] 2.1 Log successful application startup.
- [x] 2.2 Log database connectivity check result.
- [x] 2.3 Log startup failures.
- [x] 2.4 Add HTTP request logging middleware.
- [x] 2.5 Add or propagate `X-Request-Id`.

## 3. Business and Persistence Logging

- [x] 3.1 Add logs in service-layer key operations.
- [x] 3.2 Add repository/database error logs.
- [x] 3.3 Add storage/Git repository error logs.
- [x] 3.4 Exclude or mask sensitive values.

## 4. Documentation and Verification

- [x] 4.1 Update README.
- [ ] 4.2 Run backend compile.
- [ ] 4.3 Run backend tests.

Verification note: backend compile/tests could not be run in the current environment because `mvn` is not installed and the project does not include `mvnw`/`mvnw.cmd`.
