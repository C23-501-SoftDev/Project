## 1. Configuration

- [x] 1.1 Add `AiProperties` (`app.ai.enabled|base-url|api-key|model|timeout-seconds`) with an `isConfigured()` gate.
- [x] 1.2 Add `AiConfig` with `@EnableConfigurationProperties` and a named `aiRestClient` with connect/read timeouts.
- [x] 1.3 Add the `app.ai.*` block to `application.yml`, all values overridable by `AI_*` environment variables.
- [x] 1.4 Pass `AI_*` through to the container in `docker-compose.yml` with safe defaults (`enabled=false`, empty key).
- [x] 1.5 Add the `.env` block with a key placeholder (the real key is never committed — `.env` is git-ignored).

## 2. Application Layer

- [x] 2.1 Add `AiTextService` with the fixed action catalogue (formal, professional, simple, friendly, shorter, longer, grammar).
- [x] 2.2 Build the system prompt server-side; require Markdown and source language to be preserved and the bare result returned.
- [x] 2.3 Call `POST {base-url}/chat/completions` with `Authorization: Bearer`, parse `choices[0].message.content`.
- [x] 2.4 Wrap provider failures in `AiServiceException`; reject unknown actions and blank text with `IllegalArgumentException`.

## 3. REST API

- [x] 3.1 Add `GET /api/ai/status` returning `{enabled, actions[]}`.
- [x] 3.2 Add `POST /api/ai/transform` returning `{result}`.
- [x] 3.3 Map states to status codes: `503` not configured, `400` bad action/blank text, `502` provider error.
- [x] 3.4 Add `AiTransformRequest` with validation (`@NotBlank`, max 20 000 characters) and Swagger annotations.

## 4. Editor UI

- [x] 4.1 Add the "✨ Нейроассистент" button with a drop-down action panel to the editor toolbar (`document-edit.html`).
- [x] 4.2 Add the same control to the create-document form (`document-new.html`).
- [x] 4.3 Show the button only when `GET /api/ai/status` reports `enabled`.
- [x] 4.4 Send the selected fragment, or the whole text when there is no selection; replace in place via `setRangeText`.
- [x] 4.5 Show progress on the button, re-render the Markdown preview, and surface errors as toasts.

## 5. Verification

- [x] 5.1 Compile the backend (`mvn compile`).
- [x] 5.2 Run the backend test suite (no regressions).
- [ ] 5.3 Configure a real API key and verify a transformation end-to-end in the editor.
- [ ] 5.4 Verify graceful degradation: with `AI_ENABLED=false` the button is hidden and the editor works unchanged.
