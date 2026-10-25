# cookie-api-csrf-protection Specification

## Purpose
Enforced CSRF for stateless cookie authentication and frontend mutations. This specification records the implemented behavior and its verifiable acceptance criteria.

## Requirements
### Requirement: CSRF enforcement for unsafe requests
POST, PUT, PATCH and DELETE requests MUST carry a token matching the XSRF-TOKEN cookie in X-XSRF-TOKEN or the _csrf form parameter, including login and logout.
#### Scenario: Authenticated mutation without token
- **WHEN** a client supplies a valid JWT cookie but omits or corrupts the CSRF token
- **THEN** the server returns 403 and does not mutate application data
#### Scenario: Legitimate mutation
- **WHEN** the client obtains a token from GET /login and sends matching cookie/header values with authorized credentials
- **THEN** the requested operation proceeds through the normal authorization and validation rules
#### Scenario: Login CSRF
- **WHEN** login is submitted without a matching token
- **THEN** the server rejects it with 403 and does not set an authenticated JWT cookie
#### Scenario: Consecutive JWT-authenticated requests
- **WHEN** the browser performs multiple authorized reads and writes with a matching CSRF token
- **THEN** per-request JWT authentication does not clear the CSRF cookie and subsequent writes remain valid

### Requirement: Consistent frontend token handling
Frontend mutation helpers and AI actions SHALL retain the CSRF header when custom headers or multipart bodies are supplied.
#### Scenario: Caller supplies custom headers
- **WHEN** a caller adds an Accept header to an API request
- **THEN** the final request still carries X-XSRF-TOKEN and same-origin credentials

