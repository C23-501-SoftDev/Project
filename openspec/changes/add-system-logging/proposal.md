## Why

The system needs centralized, safe logging for operational diagnostics. Existing logs are scattered across services and infrastructure and do not consistently expose action, component, status, request id, or operation outcome.

## What Changes

- Add a centralized logger wrapper over the existing Spring Boot SLF4J/Logback stack.
- Add HTTP request logging middleware with request id propagation.
- Log application startup, server port, active profiles, and database connectivity checks.
- Add structured service-level logs for key business operations.
- Add repository/storage error logs without SQL text, secrets, request bodies, file paths, or personal credentials.
- Add `LOG_LEVEL`, `LOG_LEVEL_ROOT`, and `LOG_FORMAT` configuration.

## Impact

- **Backend**: Adds `infrastructure.logging` components and updates application, service, repository, storage, and REST error handling logs.
- **Configuration**: Adds environment-driven logging level and format controls.
- **Security**: Sensitive fields are masked or excluded from centralized logs. Passwords, tokens, cookies, Authorization headers, secrets, email/login values, request bodies, SQL parameter values, and file paths must not be logged.
- **API**: No public API or business logic changes.

