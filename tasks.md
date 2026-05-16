# AI-apuri — Step-by-Step Tasks for Coding Agents

Use these tasks sequentially. Each task should be completed, built, and reviewed before starting the next one.

## Global Rules for Every Agent

```text
You are building Version 1 of AI-apuri.

Do not implement web search.
Do not implement tool calling.
Do not call OpenAI cloud APIs.
Do not add telemetry, analytics, crash reporting, cloud sync, ads, or third-party backend services.
Do not log prompts, responses, API keys, or chat content.
Do not store API keys or chat history unencrypted.
Prefer small, reviewable changes.
After changes, report exactly what files were created or modified and what remains incomplete.
```

## Suggested Preamble for Every Agent Prompt

Use this before giving any task to an agent:

```text
Before making changes, inspect the existing project structure and reuse existing patterns. Keep the change scoped to this task. Do not implement features from future tasks. After finishing, summarize modified files, tests run, and any incomplete work.
```

---

# Task 01 — Create Android Project Skeleton

## Goal

Create the base Android app structure.

## Agent Prompt

```text
Create the initial AI-apuri Android project skeleton using Kotlin, Jetpack Compose, and Material 3. Use a clean package structure with core, data, domain, and ui packages. Add placeholder screens for conversation list, chat, settings, and personas. Add basic navigation between these screens. Do not implement networking, database, llama.cpp calls, encryption, web search, or tool calling.
```

## Scope

- Create Android project.
- Configure Kotlin and Compose.
- Add Material 3 theme.
- Add navigation.
- Add placeholder screens:
  - `ConversationListScreen`
  - `ChatScreen`
  - `SettingsScreen`
  - `PersonaScreen`
- Add package structure:
  - `core`
  - `data`
  - `domain`
  - `ui`

## Do Not

- Do not implement networking.
- Do not implement database.
- Do not implement web search.
- Do not implement tool calling.

## Acceptance Criteria

- App builds.
- App launches.
- User can navigate between placeholder screens.
- No external API calls exist.

---

# Task 02 — Add Core Domain Models

## Goal

Define the core data models used throughout the app.

## Agent Prompt

```text
Add the core domain models for AI-apuri Version 1. Define models for server settings, app settings, model info, conversations, messages, personas, and chat stream events. Keep the models independent from Room and network DTOs. Do not implement persistence or networking yet.
```

## Scope

Create domain models such as:

- `ServerSettings`
- `AppSettings`
- `ModelInfo`
- `Conversation`
- `ConversationSummary`
- `ConversationWithMessages`
- `Message`
- `MessageRole`
- `MessageStatus`
- `Persona`
- `ChatStreamEvent`
- `ConnectionTestResult`

## Do Not

- Do not add Room annotations yet.
- Do not add API clients yet.
- Do not implement UI behavior yet.

## Acceptance Criteria

- Domain models compile.
- Models are plain Kotlin types.
- Roles are limited to Version 1 roles:
  - `SYSTEM`
  - `USER`
  - `ASSISTANT`
- No `TOOL` role is required in Version 1.

---

# Task 03 — Add Server URL Validation and Normalization

## Goal

Make server configuration safe and predictable.

## Agent Prompt

```text
Implement server URL validation and normalization for AI-apuri. The user manually enters a llama.cpp server URL such as http://100.x.y.z:8080. Normalize trailing slashes, reject invalid schemes, support http and https, and provide helpers for /health, /v1/models, and /v1/chat/completions endpoints. Add unit tests.
```

## Scope

- Add URL validation utility.
- Add URL normalization utility.
- Add endpoint builder functions:
  - health endpoint
  - models endpoint
  - chat completions endpoint
- Add unit tests.

## Do Not

- Do not make network requests yet.
- Do not store settings yet.

## Acceptance Criteria

- Invalid URLs are rejected.
- `http` and `https` are accepted.
- Trailing slash behavior is consistent.
- Endpoint generation is tested.

---

# Task 04 — Implement Settings Storage with Encrypted Secrets

## Goal

Store server settings and API key securely.

## Agent Prompt

```text
Implement AI-apuri settings storage. Store non-secret settings using DataStore or an equivalent local settings mechanism. Store the llama.cpp API key encrypted using Android Keystore-backed protection or a suitable encrypted preferences approach. Provide a SettingsRepository exposing Flow<ServerSettings> and Flow<AppSettings>. Do not implement networking yet.
```

## Scope

- Implement `SettingsRepository`.
- Persist:
  - server base URL
  - API key
  - default model
  - app preferences
- Encrypt API key.
- Hide raw key from logs.

## Do Not

- Do not store chat history in settings.
- Do not implement server calls yet.
- Do not log secrets.

## Acceptance Criteria

- Settings survive app restart.
- API key is not stored in obvious plain text.
- Repository exposes reactive settings streams.
- No sensitive values are logged.

---

# Task 05 — Build Onboarding and Settings UI

## Goal

Allow the user to configure server settings.

## Agent Prompt

```text
Build the onboarding and settings UI for AI-apuri. The user should be able to enter a llama.cpp server base URL, enter an API key, choose whether no-key development mode is allowed, enter or select a default model, and save settings. Use Material 3 Compose components. Do not implement actual server testing yet; use placeholder state for the test button.
```

## Scope

- Add first-launch onboarding screen.
- Add settings screen fields:
  - server URL
  - API key
  - no-key development mode warning
  - default model
- Save/load settings through `SettingsRepository`.
- Add placeholder `Test connection` button state.

## Do Not

- Do not call llama.cpp yet.
- Do not implement model fetching yet.

## Acceptance Criteria

- User can edit and save settings.
- Saved settings reload correctly.
- API key field is obscured by default.
- No-key mode shows a warning.

---

# Task 06 — Implement llama.cpp API DTOs and Client Skeleton

## Goal

Create the OpenAI-compatible networking foundation.

## Agent Prompt

```text
Implement the llama.cpp OpenAI-compatible API DTOs and a LlamaApiClient skeleton for AI-apuri. Include request/response classes for /v1/models and /v1/chat/completions. Include authentication header support using Bearer API key. Do not wire it into the chat UI yet. Do not implement tool definitions, web search, or OpenAI cloud support.
```

## Scope

- Add network DTOs:
  - model list response
  - chat completion request
  - chat completion response
  - streaming delta response
- Add `LlamaApiClient` interface and implementation.
- Add auth header handling.
- Add JSON serialization.

## Do Not

- Do not include active `tools` use in Version 1.
- Do not call OpenAI cloud URLs.
- Do not add web search.

## Acceptance Criteria

- DTOs serialize/deserialize expected JSON.
- API client can be instantiated with local server settings.
- Auth header is added only when API key is present.

---

# Task 07 — Add Connection Test and Model Listing

## Goal

Make the settings screen verify the llama.cpp server.

## Agent Prompt

```text
Wire the AI-apuri settings screen to the LlamaApiClient. Implement connection testing using the configured server URL and API key. Try the health endpoint when available and fetch /v1/models. Show clear success and error states. If /v1/models fails, allow manual model entry. Add tests using a mock HTTP server.
```

## Scope

- Implement connection test use case.
- Implement model list fetching.
- Update settings UI with loading/success/error states.
- Save selected/default model.
- Add mock server tests.

## Do Not

- Do not implement chat yet.
- Do not implement web search.

## Acceptance Criteria

- Correct server and API key show success.
- Unauthorized response is shown clearly.
- Unreachable server is shown clearly.
- Model list populates when available.
- Manual model entry remains possible.

---

# Task 08 — Implement Encrypted Conversation Database

## Goal

Persist conversations and messages securely.

## Agent Prompt

```text
Implement local encrypted conversation storage for AI-apuri. Use Room with SQLCipher or an equivalent encrypted SQLite approach. Create entities and DAOs for conversations, messages, and personas. Do not store chat history in plain text. Do not implement networking or UI changes beyond repository tests.
```

## Scope

- Add encrypted database setup.
- Add Room entities:
  - `ConversationEntity`
  - `MessageEntity`
  - `PersonaEntity`
- Add DAOs.
- Add migrations baseline.
- Add repository implementations.

## Do Not

- Do not add tool call tables in Version 1.
- Do not store messages in DataStore.
- Do not log message content.

## Acceptance Criteria

- Conversations and messages can be inserted/read/deleted.
- Deleting a conversation deletes its messages.
- Personas can be inserted/read/updated/deleted.
- Database uses an encrypted-at-rest approach.

---

# Task 09 — Build Conversation List UI

## Goal

Display and manage saved conversations.

## Agent Prompt

```text
Build the AI-apuri conversation list screen. It should observe saved conversations from ConversationRepository, show title, last message preview, updated timestamp, selected model, and selected persona when available. Add create, rename, delete, and search/filter functionality. Do not implement chat sending yet.
```

## Scope

- Observe conversation summaries.
- Display list.
- Add new conversation button.
- Add rename flow.
- Add delete confirmation.
- Add local search/filter.

## Do Not

- Do not call llama.cpp yet.
- Do not add web search.

## Acceptance Criteria

- User can create a conversation.
- User can rename/delete a conversation.
- List updates reactively.
- Conversations persist after restart.

---

# Task 10 — Build Static Chat UI with Local Persistence

## Goal

Show messages and allow local message entry before model integration.

## Agent Prompt

```text
Build the AI-apuri chat screen backed by ConversationRepository. Show saved messages in a conversation, add user messages from the composer, and persist them locally. Add placeholder assistant message behavior only if useful for testing. Do not call llama.cpp yet.
```

## Scope

- Display conversation messages.
- Add message composer.
- Save user messages locally.
- Show empty state.
- Auto-scroll to latest message.

## Do Not

- Do not call model yet.
- Do not implement streaming yet.
- Do not implement tools.

## Acceptance Criteria

- Messages persist after app restart.
- UI handles empty and populated conversations.
- User messages are saved with correct role and timestamp.

---

# Task 11 — Implement Non-Streaming Chat Completion Path

## Goal

Connect chat UI to llama.cpp with the simplest request path first.

## Agent Prompt

```text
Implement non-streaming chat completion for AI-apuri. When the user sends a message, save it, build an OpenAI-compatible /v1/chat/completions request with stream=false, send it to the configured llama.cpp server, save the assistant response, and show it in the chat. Use the selected conversation model and persona/system prompt. Do not implement streaming yet.
```

## Scope

- Build message history for request.
- Add system prompt composition.
- Send non-streaming request.
- Save assistant response.
- Show loading/error states.
- Add retry from last user message.

## Do Not

- Do not implement streaming in this task.
- Do not add tool definitions.
- Do not call any cloud API.

## Acceptance Criteria

- User can send a message to local llama.cpp.
- Assistant response is saved and displayed.
- Errors are user-friendly.
- Persona/system prompt is included.

---

# Task 12 — Implement Streaming Chat Completion

## Goal

Upgrade chat responses to stream token-by-token.

## Agent Prompt

```text
Replace or extend the chat completion flow with streaming support. Use stream=true for /v1/chat/completions. Parse server-sent event chunks, append assistant deltas to a streaming message, persist the final message, and support cancellation through a Stop button. Keep non-streaming fallback if useful. Do not implement tool-call streaming.
```

## Scope

- Implement SSE/chunk parser.
- Emit `ChatStreamEvent`s through Flow.
- Update assistant message while streaming.
- Add stop/cancel support.
- Persist partial response as stopped if cancelled.
- Persist final response as complete.

## Do Not

- Do not parse tool calls.
- Do not implement web search.

## Acceptance Criteria

- Response appears token-by-token.
- Stop button cancels current request.
- Partial stopped response remains visible.
- Stream errors keep partial content and show retry.

---

# Task 13 — Add Model Switching

## Goal

Support multiple models from the server.

## Agent Prompt

```text
Implement model switching in AI-apuri. Use models fetched from /v1/models and allow manual model names. Add a model selector in settings and in the chat top bar. Store a default model globally and a selected model per conversation. Chat requests must use the conversation's selected model.
```

## Scope

- Store known models.
- Add settings default model selector.
- Add chat-level model selector.
- Save model per conversation.
- Use selected model in requests.

## Do Not

- Do not assume only one model exists.
- Do not hard-code Qwen as the only model.

## Acceptance Criteria

- User can switch models.
- New chats use default model.
- Existing chats keep their selected model.
- Requests use the selected model.

---

# Task 14 — Implement Personas and System Prompts

## Goal

Allow user-customizable assistant behavior.

## Agent Prompt

```text
Implement persona support for AI-apuri. Users can create, edit, delete, and select custom personas. Seed default personas for General assistant, Finnish helper, Coding assistant, and Research assistant without live web access. Compose the final system prompt from the app base prompt and the selected persona. Store personas locally in the encrypted database.
```

## Scope

- Seed default personas.
- Persona list screen.
- Persona editor screen/dialog.
- Default persona setting.
- Conversation persona selector.
- System prompt composition.

## Do Not

- Do not add web-search instructions to Version 1 personas.
- Do not make personas global-only; conversation-level selection is needed.

## Acceptance Criteria

- User can create/edit/delete personas.
- User can set default persona.
- User can select persona per conversation.
- Model behavior receives composed system prompt.

---

# Task 15 — Add Chat Title Generation or First-Message Naming

## Goal

Make conversation names useful.

## Agent Prompt

```text
Implement simple conversation naming for AI-apuri. For Version 1, do not add a separate model call just to generate titles unless the user explicitly chooses it. Default the conversation title from the first user message using a short local truncation rule. Allow manual rename.
```

## Scope

- Generate initial title from first user message.
- Truncate cleanly.
- Preserve manual renames.
- Optionally add setting for title behavior.

## Do Not

- Do not make extra model calls by default.
- Do not send data to external services.

## Acceptance Criteria

- New conversations get readable titles.
- Manual rename is preserved.
- No extra API call is required for title generation.

---

# Task 16 — Add Privacy and Security Controls

## Goal

Harden local privacy.

## Agent Prompt

```text
Add privacy and security controls to AI-apuri. Add optional app lock using Android biometrics or device credential where available. Add a setting to block screenshots using FLAG_SECURE. Add a clear-all-data action. Ensure prompts, responses, API keys, and chat content are not logged.
```

## Scope

- Optional app lock.
- Optional screenshot protection.
- Clear all conversations/settings action.
- Log redaction review.
- Confirm secrets and database encryption usage.

## Do Not

- Do not add cloud backup/sync.
- Do not add telemetry.

## Acceptance Criteria

- User can clear all local data.
- Optional screenshot blocking works.
- App lock works where supported.
- Sensitive content is not logged.

---

# Task 17 — Add Robust Error Handling and Diagnostics

## Goal

Make failures understandable.

## Agent Prompt

```text
Improve AI-apuri error handling. Add user-friendly error messages for invalid URL, unreachable server, unauthorized API key, model not found, timeout, malformed response, and streaming interruption. Add expandable technical details that redact secrets. Add a diagnostics export that excludes chat content and API keys.
```

## Scope

- Central error model.
- Error mapper for network/API errors.
- Expandable technical details UI.
- Retry actions.
- Diagnostics export without sensitive data.

## Do Not

- Do not include prompts, responses, API keys, or chat content in diagnostics.

## Acceptance Criteria

- Common failures show helpful messages.
- Technical details redact secrets.
- Retry paths work.

---

# Task 18 — Add Tests for Critical Logic

## Goal

Protect the app from regressions.

## Agent Prompt

```text
Add tests for AI-apuri critical logic. Cover URL normalization, endpoint generation, auth header behavior, OpenAI-compatible DTO serialization, streaming parser behavior, repository behavior, persona prompt composition, and error mapping. Use mock server tests for llama.cpp client behavior.
```

## Scope

- Unit tests.
- Mock HTTP tests.
- Repository tests.
- Parser tests.

## Do Not

- Do not add flaky UI tests unless the project already supports them.
- Do not require a real llama.cpp server for automated tests.

## Acceptance Criteria

- Tests pass locally.
- Critical logic has coverage.
- Mock server tests do not require internet.

---

# Task 19 — Add README and Developer Setup Docs

## Goal

Make the project understandable and runnable.

## Agent Prompt

```text
Create or update the AI-apuri README. Document the app purpose, Version 1 scope, explicit non-goals, required llama.cpp server setup, example server command, Tailscale recommendation, API key recommendation, Android build steps, privacy notes, and troubleshooting. Make clear that web search and tool calling are not included in Version 1.
```

## Scope

- README.
- Setup steps.
- Server example.
- Troubleshooting.
- Privacy notes.
- V1 non-goals.

## Acceptance Criteria

- A new developer can build the app.
- A user can configure llama.cpp.
- V1 scope is clear.

---

# Task 20 — Final V1 Integration Pass

## Goal

Verify the whole app as a coherent Version 1 release.

## Agent Prompt

```text
Perform a final Version 1 integration pass for AI-apuri. Review the full app against the specification and acceptance criteria. Fix build errors, obvious UI inconsistencies, missing error states, and incomplete wiring. Do not add new major features. Do not implement web search or tool calling. Produce a final checklist of what works and what remains incomplete.
```

## Scope

- Build verification.
- Manual checklist review.
- Small bug fixes.
- Consistency cleanup.
- Final status report.

## Do Not

- Do not introduce major new architecture.
- Do not add V2 features.

## Acceptance Criteria

Core V1 flow works end-to-end:

- Configure server.
- Enter API key.
- Test connection.
- Select model.
- Create conversation.
- Send message.
- Stream response.
- Stop response.
- Save encrypted history.
- Use persona.
- Restart app and see saved conversations.
- No web search exists.
- No tool calling exists.
- No third-party/cloud service calls exist.
