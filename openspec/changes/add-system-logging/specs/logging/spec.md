## ADDED Requirements

### Requirement: Centralized system logging
The system SHALL provide centralized logging for key application events using the existing Spring Boot SLF4J/Logback logging stack.

#### Scenario: Application starts successfully
- **WHEN** the application is ready
- **THEN** the system SHALL write an INFO log entry with component, action, status, application name, active profiles, and server port.

#### Scenario: Application startup fails
- **WHEN** configuration, database initialization, or server startup fails before the application is ready
- **THEN** the system SHALL write an ERROR log entry with safe diagnostic fields and rethrow the original error.

#### Scenario: Database connectivity is checked
- **WHEN** the application becomes ready
- **THEN** the system SHALL verify the configured datasource and log success or failure without logging credentials or JDBC URLs.

### Requirement: HTTP request logging
The system SHALL log incoming HTTP requests through middleware.

#### Scenario: HTTP request completed
- **WHEN** an HTTP request is processed
- **THEN** the system SHALL log method, path, status code, duration, client IP if available, and request_id.

#### Scenario: Request id is missing
- **WHEN** an HTTP request has no safe `X-Request-Id` header
- **THEN** the system SHALL generate a request_id and return it in the `X-Request-Id` response header.

### Requirement: Service operation logging
The system SHALL log key service-layer operations.

#### Scenario: Service operation succeeds
- **WHEN** a key business operation completes
- **THEN** the system SHALL write an INFO log entry with component, action, status, and safe entity identifiers where applicable.

#### Scenario: Service operation fails
- **WHEN** a key business operation fails
- **THEN** the system SHALL write an ERROR or WARN log entry and continue returning or throwing errors according to existing behavior.

### Requirement: Repository and storage error logging
The system SHALL log repository, database, and storage operation failures.

#### Scenario: Database write fails
- **WHEN** a repository save or delete operation fails
- **THEN** the system SHALL log an ERROR event with the repository component, action, status, and safe entity identifiers.

#### Scenario: Git content storage fails
- **WHEN** Git content save, move, read, or delete fails
- **THEN** the system SHALL log an ERROR event without logging file paths, content, author email, or document titles.

### Requirement: Logging configuration
The system SHALL allow logging behavior to be configured through environment variables.

#### Scenario: Application log level is configured
- **WHEN** `LOG_LEVEL` is set
- **THEN** the system SHALL apply it to the `com.knowledgebase` logger.

#### Scenario: Centralized event format is configured
- **WHEN** `LOG_FORMAT=json` is set
- **THEN** centralized application event payloads SHALL be emitted as JSON objects.

### Requirement: Sensitive data protection
The system SHALL NOT log secrets or sensitive user data.

#### Scenario: Sensitive fields are present
- **WHEN** data contains password, token, cookie, authorization, secret, credential, login, or email fields
- **THEN** the centralized logger SHALL mask or exclude those values.

#### Scenario: Request contains sensitive data
- **WHEN** an HTTP request contains cookies, Authorization headers, tokens, passwords, request bodies, or secret fields
- **THEN** the HTTP logging middleware SHALL NOT log those values.

#### Scenario: SQL or file storage contains sensitive data
- **WHEN** database or storage operations fail
- **THEN** logs SHALL NOT include full SQL statements with parameters, file contents, file paths, document titles, or personal credentials.
