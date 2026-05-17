# Developer Setup Guide

This document covers how to set up, build, and develop AI-apuri locally.

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 | Required by `compileOptions` |
| Android SDK | compileSdk 35 | minSdk 26, targetSdk 35 |
| Android Studio | Latest stable | Recommended IDE |
| Gradle | Managed by wrapper | `./gradlew` |
| Kotlin | Managed by plugin | See `build.gradle.kts` |

### Checking Your Environment

Run the project's environment check script:

```bash
./env-check.sh
```

This verifies JDK version, Android SDK availability, and other requirements.

---

## Building

### Debug Build

```bash
./gradlew assembleDebug
```

The APK is produced at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Clean Build

```bash
./gradlew clean
./gradlew assembleDebug
```

---

## Running Tests

```bash
# All unit tests
./gradlew test

# Connected device tests
./gradlew connectedAndroidTest

# Specific test
./gradlew testDebug --tests "com.aiapuri.core.util.ServerUrlValidatorTest"
```

### Test Coverage

The project includes tests for:

| Area | Test File |
|---|---|
| URL validation | `ServerUrlValidatorTest` |
| Error mapping | `ErrorMapperTest` |
| System prompt composition | `SystemPromptTest` |
| Streaming parser | `StreamingParserTest` |
| DTO serialization | `DtoSerializationTest` |
| API client (mock server) | `LlamaApiClientMockServerTest` |
| API client streaming | `LlamaApiClientStreamingTest` |
| Connection test use case | `ConnectionTestUseCaseTest` |
| Title generation | `TitleGeneratorTest` |
| App error model | `AppErrorTest` |

---

## Project Structure

```
apuri/
├── app/
│   ├── src/main/java/com/aiapuri/
│   │   ├── AiapuriApplication.kt          # Application entry, dependency setup
│   │   ├── core/                          # Core utilities and models
│   │   │   ├── model/                     # Domain models (Conversation, Message, etc.)
│   │   │   ├── security/                  # Encryption, app lock
│   │   │   ├── database/                  # Room database, content encryptor
│   │   │   └── util/                      # URL validation, error mapping, diagnostics
│   │   ├── data/                          # Data layer
│   │   │   ├── llama/                     # API client, DTOs
│   │   │   ├── conversation/              # Conversation DAO + repository
│   │   │   ├── persona/                   # Persona DAO + repository + seeder
│   │   │   └── settings/                  # DataStore settings repository
│   │   ├── domain/                        # Use cases
│   │   │   ├── chat/                      # Chat completion, streaming, system prompt
│   │   │   ├── model/                     # Connection test use case
│   │   │   └── persona/                   # Persona seeder
│   │   └── ui/                            # Compose UI
│   │       ├── app/                       # Navigation, main activity
│   │       ├── chat/                      # Chat screen + viewmodel
│   │       ├── conversations/             # Conversation list + viewmodel
│   │       ├── onboarding/                # First-run onboarding
│   │       ├── settings/                  # Settings screen + viewmodel
│   │       ├── personas/                  # Persona management
│   │       ├── diagnostics/               # Diagnostics export
│   │       ├── lock/                      # App lock screen
│   │       ├── components/                # Shared UI components
│   │       └── theme/                     # Material 3 theme
│   ├── src/test/                          # Unit tests
│   └── src/androidTest/                   # Instrumented tests
├── build.gradle.kts                       # Project-level build config
├── app/build.gradle.kts                   # App-level build config + dependencies
└── settings.gradle.kts                    # Settings
```

---

## Key Dependencies

| Library | Purpose |
|---|---|
| **Jetpack Compose** | UI framework |
| **Material 3** | Design system |
| **Navigation Compose** | Screen navigation |
| **OkHttp** | HTTP client + SSE streaming |
| **Kotlinx Serialization** | JSON serialization |
| **Room** | Local database |
| **DataStore Preferences** | Settings storage |
| **AndroidX Biometric** | App lock |
| **MockWebServer** | Test HTTP mocking |

---

## Development Tips

### Testing Against a Real Server

For development, you can run llama.cpp locally:

```bash
llama-server \
  --host 0.0.0.0 \
  --port 8080 \
  --api-key "dev-key-123" \
  --jinja \
  --model /path/to/model.gguf
```

Then configure the app with `http://<your-local-ip>:8080`.

### Network Security

The app uses a custom `network_security_config.xml` that allows cleartext traffic for local development. This is required for `http://` server URLs.

### Logging

The app includes a `RedactingLog` utility that replaces sensitive values (API keys, model IDs) with `[REDACTED]` in log output. Use this instead of `Log.d`/`Log.i` when logging might contain sensitive data.

### Adding New Screens

1. Define the route in `Routes.kt`
2. Create the screen composable in `ui/<feature>/`
3. Add the route to `AppNavigation.kt` NavHost
4. Create a ViewModel if needed

### Adding New Domain Models

1. Define the model in `core/model/`
2. If persisted, add Room entity in `data/<feature>/`
3. Add DAO methods
4. Implement repository
5. Add use case in `domain/`

---

## Encryption Architecture

### API Key Encryption

- **Key storage**: Android Keystore (AES-GCM)
- **Cipher**: AES/GCM/NoPadding
- **Storage**: Encrypted value in SharedPreferences
- **Class**: `EncryptedStringStorage`

### Message Content Encryption

- **Key storage**: Android Keystore (AES-GCM)
- **Cipher**: AES/GCM/NoPadding
- **Storage**: Encrypted field values in Room
- **Class**: `ContentEncryptor`
- **Applied at**: Repository layer (encrypt on write, decrypt on read)

### Key Rotation

Keys are created on first use and persist across app restarts. Clearing all data generates new keys.

---

## Debugging

### Common Issues

| Problem | Fix |
|---|---|
| KSP errors | Run `./gradlew clean` and rebuild |
| Compose preview errors | Sync project with Gradle files |
| Room schema conflicts | Clean build, check entity annotations |
| Test failures on CI | Ensure tests don't require real server |

### Logcat Filter

```
logcat -s Aiapuri:V
```

---

## Contributing Guidelines

1. **Keep changes scoped** — one feature per PR
2. **No telemetry** — never add analytics or tracking
3. **Encrypt sensitive data** — use `ContentEncryptor` for content, `EncryptedStringStorage` for secrets
4. **Use `RedactingLog`** — never log API keys or chat content
5. **Test critical paths** — add unit tests for new logic
6. **Follow V1 scope** — no web search, no tool calling, no cloud services
