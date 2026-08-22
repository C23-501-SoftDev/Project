## 1. Domain and Configuration

- [x] 1.1 Add `EmailMessage` value object and `EmailSender` port.
- [x] 1.2 Add `DocumentUpdatedEvent` domain event.
- [x] 1.3 Add `spring-boot-starter-mail` dependency.
- [x] 1.4 Add `spring.mail.*` and `app.notifications.*` configuration.
- [x] 1.5 Disable the actuator mail health indicator.

## 2. Infrastructure

- [x] 2.1 Add `NotificationProperties` (`app.notifications.*`).
- [x] 2.2 Add `NotificationConfig` with a dedicated async mail executor.
- [x] 2.3 Add `SpringMailEmailSender` (active when notifications enabled).
- [x] 2.4 Add `LoggingEmailSender` (active when notifications disabled).

## 3. Application and Event Wiring

- [x] 3.1 Add `NotificationService` with AFTER_COMMIT event listeners.
- [x] 3.2 Add the test-email use case (`sendTestEmail`).
- [x] 3.3 Publish `SpacePermissionGrantedEvent` from `SpaceService.grantPermission`.
- [x] 3.4 Publish `DocumentUpdatedEvent` from `DocumentService.updateDocument`.

## 4. REST API

- [x] 4.1 Add `POST /api/admin/notifications/test` (ADMIN only).
- [x] 4.2 Add `SendTestEmailRequest` and `TestEmailResponse` DTOs with Swagger annotations.

## 5. Documentation and Verification

- [x] 5.1 Update README (notifications section + environment variables).
- [x] 5.2 Update `.env.example`.
- [x] 5.3 Update feature registry (json + md).
- [x] 5.4 Add integration tests for notifications.
- [ ] 5.5 Run backend compile.
- [ ] 5.6 Run backend tests.
