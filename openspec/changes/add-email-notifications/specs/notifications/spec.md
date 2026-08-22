## ADDED Requirements

### Requirement: Asynchronous email notifications

The system SHALL send email notifications asynchronously in response to domain events, without blocking the originating request thread.

#### Scenario: Notification is sent after commit

- **WHEN** a business operation that publishes a notifiable event completes and its transaction commits
- **THEN** the system SHALL compose and dispatch the corresponding email on a dedicated mail thread pool.

#### Scenario: Originating operation is rolled back

- **WHEN** the transaction that published a notifiable event is rolled back
- **THEN** the system SHALL NOT send any email for that event.

#### Scenario: Email delivery fails

- **WHEN** SMTP delivery of a notification fails
- **THEN** the system SHALL log the failure without leaking recipient addresses and SHALL NOT propagate the error to the business operation.

### Requirement: Event-driven notification triggers

The system SHALL send an email for each of the following domain events.

#### Scenario: User is created

- **WHEN** a new user is created
- **THEN** the system SHALL send a welcome email to the new user's address.

#### Scenario: Space permission is granted

- **WHEN** an administrator grants a space permission to a user
- **THEN** the system SHALL send the user an email naming the permission type and the space.

#### Scenario: Document is updated

- **WHEN** a document is updated
- **THEN** the system SHALL notify the space members who have permissions in that space, excluding the editor, and only active users.

### Requirement: Configurable email delivery

The system SHALL allow email delivery to be enabled or disabled by configuration and SHALL start without a configured SMTP server.

#### Scenario: Notifications are disabled

- **WHEN** `app.notifications.enabled` is `false` or unset
- **THEN** the system SHALL select a no-op sender that logs the skipped email instead of contacting an SMTP server.

#### Scenario: Notifications are enabled

- **WHEN** `app.notifications.enabled` is `true`
- **THEN** the system SHALL deliver email through the configured `spring.mail.*` SMTP server using `JavaMailSender`.

#### Scenario: Mail server availability does not affect health

- **WHEN** the SMTP server is unreachable
- **THEN** `/actuator/health` SHALL NOT report DOWN because of the mail subsystem.

### Requirement: Administrator test email

The system SHALL let an administrator send a test email to verify SMTP configuration.

#### Scenario: Admin sends a test email

- **WHEN** an administrator calls `POST /api/admin/notifications/test`
- **THEN** the system SHALL queue a test email and respond `202 Accepted` with the actual recipient, the queue status, and whether real delivery is enabled.

#### Scenario: Recipient is omitted

- **WHEN** an administrator sends a test email without a recipient
- **THEN** the system SHALL use the configured `app.notifications.admin-email` as the recipient.

#### Scenario: Recipient is invalid

- **WHEN** the test-email request contains a syntactically invalid email address
- **THEN** the system SHALL respond `400 Bad Request`.

#### Scenario: Non-administrator attempts a test email

- **WHEN** a non-administrator calls `POST /api/admin/notifications/test`
- **THEN** the system SHALL respond `403 Forbidden` and SHALL NOT send any email.
