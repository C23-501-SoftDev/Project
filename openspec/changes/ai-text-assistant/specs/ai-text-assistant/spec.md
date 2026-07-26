## ADDED Requirements

### Requirement: Text transformation by a fixed action catalogue

The system SHALL rewrite a supplied Markdown fragment according to one of a fixed set of transformation actions, and SHALL build the model prompt on the server.

#### Scenario: Author transforms text

- **WHEN** an authenticated user submits text together with a supported action key
- **THEN** the system SHALL return the rewritten text, preserving the Markdown formatting and the language of the original.

#### Scenario: Unknown action

- **WHEN** the request carries an action key that is not in the catalogue
- **THEN** the system SHALL respond `400 Bad Request` and SHALL NOT contact the provider.

#### Scenario: Blank text

- **WHEN** the request carries empty or blank text
- **THEN** the system SHALL respond `400 Bad Request` and SHALL NOT contact the provider.

#### Scenario: Oversized text

- **WHEN** the submitted text exceeds the allowed length
- **THEN** the system SHALL reject the request with a validation error and SHALL NOT contact the provider.

#### Scenario: Client cannot supply a prompt

- **WHEN** a client submits a request
- **THEN** the system SHALL accept only the text and an action key, and SHALL construct the model instruction itself.

### Requirement: Configurable and optional AI provider

The system SHALL treat the assistant as optional and SHALL operate normally when it is not configured.

#### Scenario: Assistant is disabled or has no key

- **WHEN** the assistant is disabled or no API key is configured
- **THEN** `GET /api/ai/status` SHALL report `enabled: false` and `POST /api/ai/transform` SHALL respond `503 Service Unavailable`.

#### Scenario: Editor hides an unavailable assistant

- **WHEN** the status endpoint reports that the assistant is unavailable
- **THEN** the document editor SHALL NOT display the assistant control and SHALL remain fully usable.

#### Scenario: Provider fails or times out

- **WHEN** the provider returns an error, is unreachable, or exceeds the configured timeout
- **THEN** the system SHALL respond `502 Bad Gateway` with a diagnostic message and SHALL NOT modify the document.

#### Scenario: Provider and model are configurable

- **WHEN** an operator sets the base URL and model identifier through configuration
- **THEN** the system SHALL use them without code changes, defaulting to the provider's cheapest general-purpose text model.

### Requirement: API key confidentiality

The system SHALL keep the provider API key on the server side.

#### Scenario: Browser initiates a transformation

- **WHEN** the editor requests a transformation
- **THEN** the request SHALL go to the application's own endpoint, and the application SHALL attach the API key when calling the provider, so the key is never delivered to the browser.

### Requirement: In-place application in the editor

The document editor SHALL offer the transformation actions and apply the result to the edited text.

#### Scenario: Author transforms a selected fragment

- **WHEN** an author selects a fragment and picks an action
- **THEN** the editor SHALL replace only the selected fragment with the result and SHALL refresh the preview.

#### Scenario: Author transforms with no selection

- **WHEN** an author picks an action without selecting anything
- **THEN** the editor SHALL transform the whole text of the field.

#### Scenario: Transformation fails

- **WHEN** the transformation request fails
- **THEN** the editor SHALL show an error notification and SHALL leave the text unchanged.
