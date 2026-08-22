## Context

Documents are edited as raw Markdown in a `<textarea>` (`document-edit.html`, `document-new.html`); there is no rich-text editor, so a transformation is a pure string-in/string-out operation over the selection. The provider routerai.ru exposes an OpenAI-compatible API (`POST {base}/chat/completions`, `Authorization: Bearer <key>`), which lets us integrate with `RestClient` and no vendor SDK.

**Constraints**:
- The API key must never be exposed to the browser or committed to git.
- The application must start and the editor must work with no AI configuration at all.
- A slow or failing provider must not hang a request thread indefinitely or corrupt the document.
- Cost is per token and the budget is small, so the default model is the cheapest general-purpose text model.

## Goals / Non-Goals

**Goals:**
- Rewrite a selected fragment (or the whole document) in a chosen style, in place.
- Server-side prompt construction from a fixed action list.
- Configuration-driven on/off with graceful UI degradation.
- Provider-agnostic integration through the OpenAI-compatible contract.

**Non-Goals:**
- Streaming responses token by token.
- Conversation/chat history or multi-turn refinement.
- Undo stack beyond the browser's native textarea undo.
- Generating whole documents from a prompt, or answering questions about the knowledge base (no RAG).
- Per-user quotas or cost accounting.

## Decisions

### Decision 1: Fixed server-side action catalogue, client sends only a key

**Chosen**: `AiTextService` holds an ordered map `action → instruction`; the request DTO carries `action` plus `text`, never a prompt.

**Rationale**:
- The browser cannot steer the model into arbitrary behaviour (prompt injection through the API surface).
- Wording of the prompts can be tuned centrally without touching the frontend.
- `GET /api/ai/status` publishes the catalogue, so the UI cannot drift from the server.

**Trade-offs**:
- No free-form user instruction. Out of scope for this change; the action list covers the requested "change the type of text".

### Decision 2: Server-side proxy instead of calling the provider from the browser

**Chosen**: The browser calls `POST /api/ai/transform`; the server calls the provider.

**Rationale**:
- A browser-side call would ship the API key to every client.
- Keeps the provider swappable and lets us enforce size limits and auth.

**Trade-offs**:
- Provider latency occupies a request thread. Bounded by an explicit connect/read timeout (default 60 s).

### Decision 3: `enabled` + non-blank key gate, UI hides itself

**Chosen**: `AiProperties.isConfigured()` requires `enabled=true` and a non-blank key. The endpoint answers `503` when unconfigured, and the frontend queries `/api/ai/status` before showing the button.

**Rationale**:
- The feature is optional; a repository clone without a key must not show a button that always fails.
- Distinguishes "not configured" (503) from "provider failed" (502).

**Trade-offs**:
- One extra status request per editor load. It is tiny and cached by nothing — acceptable.

### Decision 4: Selection-scoped replacement via `setRangeText`

**Chosen**: If the textarea has a selection, only that range is replaced; otherwise the whole value is replaced.

**Rationale**:
- Matches the mental model of "reword this paragraph" and preserves the rest of the document.
- `setRangeText` keeps the browser's native undo history, so the author can revert with Ctrl+Z.

**Trade-offs**:
- Replacing the whole document when nothing is selected is a large change; the panel states this explicitly, and undo is available.

### Decision 5: Dedicated `RestClient` bean with explicit timeouts

**Chosen**: `AiConfig` builds a named `aiRestClient` with connect and read timeouts from `app.ai.timeout-seconds`.

**Rationale**:
- The default request factory has no timeout; a hanging provider would pin threads.
- An isolated bean keeps AI HTTP settings from affecting other clients.

**Trade-offs**:
- A long transformation may hit the timeout and surface as `502`. The limit is configurable.

### Decision 6: Model as configuration, cheapest general-purpose default

**Chosen**: `AI_MODEL` defaults to `deepseek/deepseek-v4-pro`.

**Rationale**:
- Cheapest general-purpose text model at the provider (62 ₽ / 124 ₽ per 1M input/output tokens), which suits a small budget.
- Cheaper models exist but are code-oriented; switching is a one-line environment change.

**Trade-offs**:
- Quality is tied to a third-party model that may change; the identifier is not pinned to a snapshot.

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| API key leaks | High | Key only in git-ignored `.env`, injected as an env var, never sent to the browser; placeholder committed |
| Provider outage or slowness | Medium | Explicit timeouts, `502` with a message, editor remains fully usable |
| Model returns text wrapped in code fences or commentary | Medium | System prompt demands the bare result; response trimmed |
| Author loses text by transforming the whole document | Medium | Selection-scoped by default, `setRangeText` preserves native undo, panel states the behaviour |
| Token cost grows with document size | Low/Medium | 20 000-character request cap, cheapest default model |
