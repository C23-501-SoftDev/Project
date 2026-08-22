## Context

The Knowledge Base already publishes domain events (`UserCreatedEvent` was published by `UserService` but had no listener). US4.3.1 / US4.3.2 require asynchronous email notifications and admin SMTP configuration with a test-email check. The implementation must respect the existing Clean/Hexagonal architecture and add no behavior to the originating operations beyond firing a notification.

**Architecture**: Clean/Hexagonal — 4 layers:
- `domain/notification/*` — `EmailMessage` (value object), `EmailSender` (port).
- `domain/event/*` — `DocumentUpdatedEvent` (new), reuses `UserCreatedEvent`, `SpacePermissionGrantedEvent`.
- `application/service/*` — `NotificationService` (event listeners + test-email use case).
- `infrastructure/notification/*` — `SpringMailEmailSender` (real SMTP adapter), `LoggingEmailSender` (no-op adapter).
- `infrastructure/config/*` — `NotificationProperties`, `NotificationConfig` (async pool).
- `interfaces/rest/controller/*` — `NotificationAdminController`.

**Constraints**:
- No public interface changes to existing services beyond adding a constructor dependency (`ApplicationEventPublisher`).
- Notification failures must not break business operations.
- The app must start in dev/test without a configured SMTP server.
- `/actuator/health` must not flip to DOWN when SMTP is unreachable (docker healthcheck depends on it).

## Goals / Non-Goals

**Goals:**
- Asynchronous email delivery that never blocks the request thread.
- Emails sent only after the originating transaction commits.
- Configuration-driven on/off switch for real SMTP delivery.
- Admin test-email endpoint reporting queue status.

**Non-Goals:**
- Storing SMTP settings in the database with a runtime editor UI (US4.3.2 allows config-file storage; we use environment-driven `spring.mail.*`).
- HTML/templated emails (plain text is sufficient for the MVP).
- Delivery receipts, retries, or a persistent outbox.
- Per-user notification preferences / unsubscribe.

## Decisions

### Decision 1: `@TransactionalEventListener(AFTER_COMMIT)` over `@EventListener`

**Chosen**: Listeners fire after the originating transaction commits.

**Rationale**:
- A rolled-back operation (e.g., failed permission grant) must not send a notification.
- `fallbackExecution=true` keeps listeners working if an event is ever published outside a transaction.

**Trade-offs**:
- The listener runs in a new read-only transaction to resolve recipient/space data — one extra short read per event. Acceptable.

### Decision 2: Two `@ConditionalOnProperty` sender beans

**Chosen**: `SpringMailEmailSender` when `app.notifications.enabled=true`; otherwise `LoggingEmailSender`.

**Rationale**:
- Dev/test boot without an SMTP server or `JavaMailSender` configuration.
- The domain/application layers depend only on the `EmailSender` port; the adapter is selected by configuration.

**Trade-offs**:
- Two beans to maintain. Both are small and share the port contract.

### Decision 3: Dedicated async executor, errors swallowed in the adapter

**Chosen**: `@Async("mailTaskExecutor")` on `SpringMailEmailSender.send`; `MailException` is caught and logged, not rethrown. `CallerRunsPolicy` on a bounded queue.

**Rationale**:
- SMTP latency must not block request threads (US4.3.1).
- A notification failure must not break the business operation.
- `CallerRunsPolicy` prevents silent loss under burst load (sender momentarily runs synchronously instead of dropping the task).

**Trade-offs**:
- No retry/outbox — a permanently failing SMTP loses the message (logged). Acceptable for the MVP.

### Decision 4: Disable the actuator mail health indicator

**Chosen**: `management.health.mail.enabled=false`.

**Rationale**:
- Spring Boot auto-registers a mail health indicator that performs a live SMTP connection check; an unreachable mail server would flip `/actuator/health` to DOWN and fail the docker healthcheck.
- The test-email endpoint is the explicit connection check for US4.3.2.

**Trade-offs**:
- Mail server status is not surfaced via `/actuator/health`; it is verified on demand via the admin test endpoint.

### Decision 5: Test-email endpoint returns 202 Accepted

**Chosen**: `POST /api/admin/notifications/test` returns 202 with `{recipient, queued, notificationsEnabled}`.

**Rationale**:
- Delivery is asynchronous; 202 communicates "accepted/queued", not "delivered".
- `notificationsEnabled` lets the admin see whether real SMTP delivery is active or the message was only logged.

**Trade-offs**:
- Success of queueing is not success of delivery; the admin confirms receipt out of band.

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| SMTP misconfiguration in prod | Medium | Test-email endpoint; failures logged via `SystemLogger` without leaking addresses |
| Document-update notification fans out to many recipients | Low/Medium | Async pool with bounded queue + `CallerRunsPolicy`; editor excluded; only active users |
| No retry/outbox — transient SMTP outage loses a message | Low | Logged for diagnostics; acceptable for MVP, can add an outbox later |
| Listener read-only transaction adds a query per event | Low | Lookups are by primary key / indexed `space_id` |
