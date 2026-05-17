# AI-apuri

> A private Android chat client for your local `llama.cpp` server.

AI-apuri is a **private, local-first** Android chat application that connects to a `llama.cpp` server over your local network or Tailscale. It works like a simple ChatGPT-style mobile client, but uses **your own model** running on **your own hardware**. No cloud, no telemetry, no third-party services.

---

## Quick Start

### 1. Run a llama.cpp Server

```bash
llama-server \
  --host 0.0.0.0 \
  --port 8080 \
  --api-key "CHANGE_ME_LONG_RANDOM_KEY" \
  --jinja \
  --model /path/to/your-model.gguf
```

### 2. Build the Android App

```bash
./gradlew assembleDebug
```

Install the APK on your device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configure the App

On first launch, AI-apuri guides you through onboarding:

1. Enter your llama.cpp server URL (e.g., `http://100.x.y.z:8080`)
2. Enter your API key (recommended) or enable no-key development mode
3. Select or enter a model name (e.g., `qwen3.6-27b`)

---

## Version 1 Scope

Version 1 focuses on the core local chat experience:

| Feature | Status |
|---|---|
| Manual llama.cpp server URL configuration | ✅ |
| API key authentication (Bearer token) | ✅ |
| Connection test and model listing | ✅ |
| Streaming chat responses | ✅ |
| Multiple saved conversations | ✅ |
| Encrypted-at-rest chat history | ✅ |
| Model switching (per conversation) | ✅ |
| Custom personas / system prompts | ✅ |
| Secure local settings | ✅ |
| Stop generation | ✅ |
| Retry failed messages | ✅ |
| Conversation search/filter | ✅ |
| Biometric app lock (optional) | ✅ |
| Screenshot blocking (optional) | ✅ |
| Diagnostics export (no sensitive data) | ✅ |
| Clear all data | ✅ |

---

## What Version 1 Does **Not** Do

The following features are **explicitly excluded** from Version 1:

- ❌ Web search
- ❌ Tool calling
- ❌ Autonomous agents
- ❌ File attachments / PDF parsing
- ❌ Image input
- ❌ Cloud sync
- ❌ OpenAI cloud API usage
- ❌ Telemetry or analytics
- ❌ Ads
- ❌ Crash reporting that includes chat content
- ❌ Any third-party backend service

Version 1 is intentionally boring, reliable, private, and easy to extend.

---

## Architecture

```
Android App
  ├── UI Layer (Jetpack Compose + Material 3)
  ├── Presentation Layer (ViewModels, UI state, UI events)
  ├── Domain Layer (Use cases, business rules)
  ├── Data Layer
  │     ├── LlamaApiClient (OkHttp, SSE streaming)
  │     ├── ConversationRepository (Room + encryption)
  │     ├── PersonaRepository (Room)
  │     └── SettingsRepository (DataStore + Keystore)
  ├── Local Storage
  │     ├── Room database (AES-GCM field encryption)
  │     └── DataStore preferences + Android Keystore
  └── Network
        └── llama.cpp server (OpenAI-compatible API)
```

### Package Structure

```
com.aiapuri/
  core/
    network/          # Network utilities
    security/         # Encryption, app lock
    database/         # Room database, content encryption
    model/            # Domain models
    util/             # URL validation, error mapping, diagnostics
  data/
    llama/            # API client, DTOs
    conversation/     # Conversation DAO, repository
    persona/          # Persona DAO, repository, seeder
    settings/         # DataStore settings repository
  domain/
    chat/             # Chat use cases (streaming, system prompt)
    conversation/     # Conversation use cases
    model/            # Model use cases
    persona/          # Persona use cases
  ui/
    app/              # Navigation, main activity
    chat/             # Chat screen and viewmodel
    conversations/    # Conversation list screen and viewmodel
    onboarding/       # First-run onboarding
    settings/         # Settings screen and viewmodel
    personas/         # Persona management screen
    diagnostics/      # Diagnostics export screen
    lock/             # App lock screen
    components/       # Shared UI components
    theme/            # Material 3 theme
```

---

## Server Setup

### Recommended Configuration

AI-apuri talks to llama.cpp via the **OpenAI-compatible API** on these endpoints:

```
<server-url>/v1/chat/completions   # Chat (streaming)
<server-url>/v1/models             # Model listing
<server-url>/health                # Health check (when available)
```

### Example Server Command

```bash
llama-server \
  --host 0.0.0.0 \
  --port 8080 \
  --api-key "CHANGE_ME_LONG_RANDOM_KEY" \
  --jinja \
  --model /path/to/qwen3.6-27b.gguf
```

The app sends `Authorization: Bearer <api_key>` headers when an API key is configured.

### Tailscale Recommendation

If your llama.cpp server is on another device, **Tailscale** provides a simple, encrypted mesh network:

1. Install Tailscale on both your server and Android device
2. Use the Tailscale IP (e.g., `100.x.y.z`) as the server URL
3. Traffic is encrypted end-to-end

```
Server URL: http://100.x.y.z:8080
```

### No-Key Development Mode

For local testing, you can enable no-key mode. The app will show a warning because traffic between the app and server is unauthenticated.

---

## Android Build Steps

### Prerequisites

- **Android Studio** (latest stable)
- **JDK 17**
- **Android SDK** (compileSdk 35, minSdk 26)
- **Gradle** (managed by wrapper)

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Run connected device tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

### Project Configuration

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 35 (Android 15)
- **compileSdk**: 35
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Networking**: OkHttp with SSE support
- **Database**: Room with AES-GCM field-level encryption
- **Settings**: DataStore Preferences + Android Keystore

---

## Privacy and Security

### What AI-apuri Does

- **All data stays on your device.** No cloud, no telemetry, no analytics.
- **Chat history is encrypted at rest** using AES-GCM with a key stored in the Android Keystore.
- **API keys are encrypted** before storage using Android Keystore-backed AES-GCM encryption.
- **Sensitive content is never logged.** A redacting logger replaces secrets with `[REDACTED]`.
- **Diagnostics export excludes** all chat content, prompts, responses, and API keys.
- **Optional biometric app lock** using Android BiometricPrompt.
- **Optional screenshot blocking** via `FLAG_SECURE`.
- **Clear all data** action wipes conversations, settings, and encryption keys.

### What AI-apuri Does Not Do

- No OpenAI cloud API calls
- No telemetry or analytics
- No cloud sync
- No web search
- No tool calling
- No crash reporting with chat content
- No third-party backend services
- No ads
- No data collection of any kind

### Encryption Details

| Data | Protection |
|---|---|
| API key | AES-GCM encrypted, key in Android Keystore |
| Message content | AES-GCM field-level encryption before Room storage |
| System prompts | AES-GCM field-level encryption before Room storage |
| Settings (non-secret) | DataStore Preferences (app-private) |

---

## Troubleshooting

### Cannot Connect to Server

1. Verify the server URL is correct (e.g., `http://100.x.y.z:8080`)
2. Ensure no trailing slashes (`http://100.x.y.z:8080/` is normalized automatically)
3. Check that the server is running and accessible from your Android device
4. If using Tailscale, verify both devices are on the same tailnet
5. The app supports `http` and `https` schemes

### Unauthorized / Bad API Key

1. Check that the API key matches the one configured in llama.cpp (`--api-key`)
2. Re-enter the key in Settings
3. If testing locally without a key, enable "no-key development mode" in Settings

### Model Not Found

1. Tap "Refresh models" in Settings to fetch the model list from the server
2. If the server doesn't support `/v1/models`, enter the model name manually
3. Model names must match exactly what llama.cpp reports

### Streaming Stops Mid-Response

1. Check the server is still running
2. Verify network connectivity (Tailscale status, local network)
3. Partial responses are saved — you can continue the conversation
4. Use the retry option on failed messages

### App Lock Not Available

- Biometric app lock requires the device to have a configured lock screen credential
- If no biometric sensor is available, device credential (PIN/pattern/password) is used as fallback

### Clear All Data

To reset the app completely:
1. Go to Settings → Privacy & Security → Clear All Data
2. This removes all conversations, settings, personas, and encryption keys
3. You will need to re-configure the server on next launch

---

## Testing

The project includes unit tests covering:

- URL validation and normalization
- Server error mapping
- System prompt composition
- Streaming parser (SSE chunk parsing)
- DTO serialization/deserialization
- API client behavior (mock server tests)
- Title generation

```bash
# Run all unit tests
./gradlew test

# Run tests with coverage (if configured)
./gradlew jacocoTestReport
```

---


## Future Plans (Not in Version 1)

Version 2 may include:

- Web search (with explicit user approval)
- File attachments and PDF ingestion
- Additional tool support

These features will always respect the privacy-first design of AI-apuri.
