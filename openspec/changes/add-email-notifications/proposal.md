## Why

Users have no automated feedback when system events affect them: a new account is created, space permissions are granted, or a document they follow is changed. The backlog (US4.3.1 / US4.3.2) requires an asynchronous email notification subsystem and an admin-facing way to configure SMTP and verify it with a test email.

## What Changes

- Add an `EmailSender` domain port and a value object `EmailMessage`.
- Add a `NotificationService` that listens to domain events and composes emails:
  - `UserCreatedEvent` → welcome email to the new user.
  - `SpacePermissionGrantedEvent` → notice to the user about granted permission.
  - `DocumentUpdatedEvent` → notice to space members (except the editor) about a document change.
- Send email asynchronously via Spring `JavaMailSender` on a dedicated thread pool, so SMTP latency never blocks the request thread.
- Send only **after** the originating transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`), so a rolled-back operation produces no email.
- Swap the sender by configuration: real SMTP sender when `app.notifications.enabled=true`, otherwise a logging no-op sender (dev/test run without a mail server).
- Add an admin endpoint `POST /api/admin/notifications/test` to send a test email and report queue status (US4.3.2, scenario 2).
- Publish `DocumentUpdatedEvent` from `DocumentService.updateDocument` and `SpacePermissionGrantedEvent` from `SpaceService.grantPermission`.

## Impact

- **Backend**: Adds `domain.notification`, `infrastructure.notification`, `NotificationService`, `NotificationAdminController`, and request/response DTOs. Wires event publishing into `DocumentService` and `SpaceService`.
- **Configuration**: Adds `spring.mail.*`, `app.notifications.*`, and disables the actuator mail health indicator so SMTP availability does not affect `/actuator/health`.
- **Dependencies**: Adds `spring-boot-starter-mail` (Spring-managed version).
- **API**: Adds one admin-only endpoint. No existing endpoint or business-logic behavior changes.
- **Errors**: Email failures are logged and never propagated to the caller — a notification failure does not break the business operation.
