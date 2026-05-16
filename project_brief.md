# AI-apuri — Project Brief for Coding Agents

## Purpose

AI-apuri is a private Android chat application for connecting to a local `llama.cpp` server over the user's local/Tailscale network.

The app is intended to work like a simple private ChatGPT-style mobile client, but it uses the user's own local model server instead of OpenAI cloud services.
## tool locations
use env-check.sh tool to check environment 

## Version 1 Scope

Version 1 must focus only on the core local chat experience:

- Android app
- Kotlin
- Jetpack Compose
- Material 3
- Manual llama.cpp server URL configuration
- OpenAI-compatible `/v1/chat/completions`
- Streaming chat responses
- Multiple saved conversations
- Local encrypted chat history
- Model switching
- Custom personas/system prompts
- Secure local settings
- API-key support for llama.cpp
- Tailscale/local network usage

## Explicit Version 1 Non-Goals

Do **not** implement these in Version 1:

- Web search
- Tool calling
- Autonomous agents
- Background browsing
- File attachments
- PDF parsing
- Image input
- Cloud sync
- OpenAI cloud API usage
- Telemetry
- Analytics
- Ads
- Crash reporting that includes chat content
- Any third-party backend service

The first version should be boring, reliable, private, and easy to extend.

## Target Server

The user runs:

- `llama.cpp` server
- OpenAI-compatible API
- Primary model: `Qwen3.6 27B`
- Connection over Tailscale
- User manually enters server URL

Example server URL:

```text
http://100.x.y.z:8080
```

Example endpoints:

```text
<serverBaseUrl>/v1/chat/completions
<serverBaseUrl>/v1/models
<serverBaseUrl>/health
```

## Recommended llama.cpp Server Startup

Use API-key authentication even over Tailscale.

```bash
llama-server \
  --host 0.0.0.0 \
  --port 8080 \
  --api-key "CHANGE_ME_LONG_RANDOM_KEY" \
  --jinja \
  --model /path/to/qwen-model.gguf
```

The Android app should send:

```http
Authorization: Bearer <api_key>
```

when an API key is configured.

## Recommended Android Stack

Use:

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines
- Flow
- Room
- Encrypted SQLite / SQLCipher or equivalent encrypted-at-rest database
- DataStore for non-secret settings
- Android Keystore or encrypted preferences for secrets
- OkHttp or Ktor Client for networking
- Hilt or Koin for dependency injection

Recommended minimum SDK:

```text
minSdk 26
```

## High-Level Architecture

Use a layered architecture.

```text
Android App
  ├── UI Layer
  │     └── Jetpack Compose screens
  ├── Presentation Layer
  │     └── ViewModels, UI state, UI events
  ├── Domain Layer
  │     └── Use cases and business rules
  ├── Data Layer
  │     ├── LlamaApiClient
  │     ├── ConversationRepository
  │     ├── PersonaRepository
  │     └── SettingsRepository
  ├── Local Storage
  │     ├── Encrypted conversation database
  │     └── Encrypted settings/secrets
  └── Network
        └── llama.cpp server over Tailscale
```

Suggested package structure:

```text
app/src/main/java/com/aiapuri/
  core/
    network/
    security/
    database/
    model/
    util/
  data/
    llama/
    conversation/
    persona/
    settings/
  domain/
    chat/
    conversation/
    model/
    persona/
  ui/
    app/
    chat/
    conversations/
    onboarding/
    settings/
    personas/
    components/
```

## Core Screens

### Onboarding / Settings

The user must be able to configure:

- Server base URL
- API key
- Default model
- No-key development mode, with warning
- Connection test
- Model list refresh

### Conversation List

Must support:

- List saved conversations
- Create conversation
- Rename conversation
- Delete conversation
- Search/filter conversations
- Show last message preview
- Show selected model/persona when available

### Chat Screen

Must support:

- Message list
- User and assistant message bubbles
- Text composer
- Send button
- Stop button during streaming
- Streaming assistant response updates
- Retry failed messages
- Model selector
- Persona selector

### Personas

Must support:

- List personas
- Create persona
- Edit persona
- Delete persona
- Set default persona
- Select persona per conversation

Seed default personas:

- General assistant
- Finnish helper
- Coding assistant
- Research assistant without live web access

## Data Requirements

Store locally:

- Server settings
- API key
- App preferences
- Conversations
- Messages
- Personas
- Model preferences

Chat history must be encrypted at rest.

API keys must be encrypted and never logged.

## Suggested Domain Models

```kotlin
data class ServerSettings(
    val baseUrl: String,
    val apiKey: String?,
    val allowNoApiKey: Boolean,
    val defaultModel: String?
)
```

```kotlin
data class ModelInfo(
    val id: String,
    val displayName: String = id
)
```

```kotlin
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val model: String,
    val personaId: String?,
    val systemPromptSnapshot: String?
)
```

```kotlin
data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
    val status: MessageStatus
)
```

```kotlin
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}
```

```kotlin
enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETE,
    STOPPED,
    ERROR
}
```

```kotlin
data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val isDefault: Boolean
)
```

## OpenAI-Compatible Chat Request

Version 1 should send plain chat completion requests without tools.

```json
{
  "model": "qwen3.6-27b",
  "messages": [
    {
      "role": "system",
      "content": "You are AI-apuri, a helpful private assistant."
    },
    {
      "role": "user",
      "content": "Hello!"
    }
  ],
  "stream": true
}
```

Do not send `tools` in Version 1.

## Streaming Requirements

The app must support streamed responses from `/v1/chat/completions`.

Expected behavior:

1. User sends message.
2. User message is saved immediately.
3. App sends request with `stream: true`.
4. Assistant message appears in streaming state.
5. Text updates as chunks arrive.
6. Stop button cancels request.
7. Final response is saved as complete.
8. Cancelled response is saved as stopped.
9. Failed response shows an error and retry option.

## System Prompt

Base Version 1 system prompt:

```text
You are AI-apuri, a private assistant running through a local model server. Be helpful, accurate, and concise.

You do not have web access in this version. Do not claim that you searched the web or checked live sources. If the user asks for current information, explain that web search is not available in this version and answer from existing knowledge only when appropriate.
```

Final prompt composition:

```text
<App base system prompt>

<Selected persona system prompt>
```

## Privacy and Security Rules

Mandatory:

- No OpenAI cloud calls.
- No telemetry.
- No cloud sync.
- No web search in Version 1.
- No tool calling in Version 1.
- Do not log prompts, responses, API keys, or chat content.
- Encrypt chat history.
- Encrypt API keys.
- Allow user to clear local data.
- Prefer Tailscale address for server connection.
- Warn clearly if user chooses no API key mode.

Recommended:

- Optional app lock using biometrics/device credential.
- Optional screenshot blocking with `FLAG_SECURE`.
- Diagnostics export must exclude chat content and API keys.

## Error Handling Requirements

Show user-friendly errors for:

- Invalid URL
- Cannot reach server
- Unauthorized / bad API key
- Server loading model
- Model not found
- Timeout
- Malformed response
- Streaming interruption

Each error should offer, when relevant:

- Retry
- Open settings
- Expandable technical details with secrets redacted

## Version 1 Acceptance Criteria

Version 1 is complete when:

- User can configure llama.cpp server URL manually.
- User can configure API key.
- App can test connection.
- App can select or manually enter a model.
- App can create multiple conversations.
- Conversations are saved locally.
- Chat history is encrypted at rest.
- User can send messages and receive streaming responses.
- User can stop generation.
- User can create and select custom personas/system prompts.
- App sends OpenAI-compatible chat requests.
- App does not send tool definitions.
- App does not perform web search.
- App does not use third-party services.
- No cloud sync or telemetry is enabled.

## Future Version 2 Ideas

Do not implement now, but keep architecture extensible for:

- Approved `web_search`
- Local/Tailscale helper service
- Brave Search API provider
- Self-hosted SearXNG provider
- Page extraction
- File attachments
- PDF ingestion
- More tools

Future web search should always require user approval.
