## Why

Authors write documentation directly in a Markdown textarea and have no help with wording. Bringing a document to a consistent style — formal for regulations, simple for instructions, shorter for summaries — is manual work, and the resulting wiki reads inconsistently. An assistant that rewrites a selected fragment in a chosen style removes that friction without taking the author out of the editor.

## What Changes

- Add an `AiTextService` that rewrites Markdown text through an OpenAI-compatible provider (routerai.ru).
- Fix a closed set of transformation actions server-side: `formal`, `professional`, `simple`, `friendly`, `shorter`, `longer`, `grammar`. The client sends an action key; the prompt is built on the server.
- Add `GET /api/ai/status` (is the assistant configured + available actions) and `POST /api/ai/transform` (`{text, action}` → `{result}`).
- Add configuration `app.ai.*` (`enabled`, `base-url`, `api-key`, `model`, `timeout-seconds`) driven by environment variables; default model `deepseek/deepseek-v4-pro` (cheapest general-purpose text model on the provider).
- Add a "✨ Нейроассистент" button to the document editor toolbar and to the create-document form; clicking it reveals the action buttons.
- Apply the result to the selected fragment, or to the whole text when nothing is selected.

## Impact

- **Backend**: New `application/service/AiTextService`, `infrastructure/config/AiProperties`, `infrastructure/config/AiConfig` (dedicated `RestClient` with timeouts), `interfaces/rest/controller/AiController`, `dto/request/AiTransformRequest`.
- **Frontend**: `templates/pages/document-edit.html` and `templates/pages/document-new.html` gain the assistant control; the button stays hidden unless `GET /api/ai/status` reports `enabled`.
- **Configuration**: `app.ai.*` in `application.yml`, `AI_*` variables passed through `docker-compose.yml`. The API key lives only in `.env` (git-ignored) — no secret is committed.
- **Security**: Endpoints require authentication like the rest of `/api/**`; the API key never reaches the browser — the browser calls our endpoint, the server calls the provider.
- **Cost**: Billed per token by the provider. Text length is capped at 20 000 characters per request.
- **Degradation**: With `app.ai.enabled=false` or a missing key the endpoints answer `503` and the UI hides the button, so the editor works exactly as before.
