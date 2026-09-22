# SALi-HCNSEC

Personal AI workspace with **HCNSEC as the primary provider**, plus a broad catalog of OpenAI-compatible and native AI APIs. Built with modern Android, Jetpack Compose, Kotlin Coroutines & Flow, and Android KeyStore security.

---

## ✨ Features
- **Chatbox-style multi-provider AI:** HCNSEC remains the default provider. Ready-made profiles cover Google AI Studio/Gemini, Groq, OpenRouter, Cerebras, SambaNova, Together AI, DeepInfra, Fireworks, Mistral, Cohere, NVIDIA NIM, Hugging Face, and OpenAI. Most profiles use the shared OpenAI-compatible contract, while Gemini uses its native streaming API.
- **Custom provider endpoints:** Add any HTTPS OpenAI-compatible base URL and optional model from Settings. This supports self-hosted gateways and new providers without an app update.
- **Provider-aware model discovery:** Models are loaded from the active provider's `/models` endpoint where supported, with manual model IDs available for providers that do not expose a catalog.
- **Reasoning Process Display:** Collapsible visual accordion for DeepSeek-R1 / HCNSEC reasoning tokens (`reasoning_content`).
- **ZIP Workspace & Codebase Analysis:** Upload ZIP files directly to extract code trees and conduct holistic architectural discussions.
- **Hardware-Backed KeyStore Security:** User API keys for every provider are securely encrypted on-device with AES-GCM and Android Keystore. API keys are never bundled in the APK or sent to a different provider than the one selected.
- **Bilingual UI (EN / فارسی):** A dependency-free localization layer switches the whole interface, with automatic RTL layout, from Settings.
- **Zero-Bug Material 3 Design:** Edge-to-edge support, custom dark theme (`#0B0C10`, `#14151F`, `#8B5CF6`), and smooth auto-scrolling.

---

## 🚀 Building the APK

### Method 1: Automatic GitHub Actions (recommended)
Every push to `main` or `arena/01a0abb1-personal-ai-hcn` runs `.github/workflows/build-apk.yml`, which:

1. Installs JDK 21 + the Android SDK packages on the runner (JDK 21 is required by Robolectric 4.16 to emulate SDK 36).
2. Runs the unit tests (`:app:testDebugUnitTest`).
3. Builds a **minified, resource-shrunk, signed release APK** (`:app:assembleRelease`).
4. Verifies the signature with `apksigner` and uploads `Sali-HCNSEC-Release-APK`.

Download `app-release.apk` from the **Actions** tab (artifact) or from the GitHub
**Release** that the workflow publishes. Each release note carries the build report
for that run: commit, APK size, SHA-256, signature, R8 keep-rule check, the test
results and the tail of the release build log.

> This repository has no `gradlew` wrapper jar, so the workflow provisions Gradle
> 9.3.1 directly instead of calling `./gradlew`.

### Method 2: Stable signing key (optional, for in-place updates)
Without configuration the workflow signs with a freshly generated key on every
run, so a new APK has to be installed over a clean uninstall. To keep one stable
identity, add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Description |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |
| `KEY_ALIAS` | key alias (defaults to `upload`) |

### Method 3: Local Android Studio
1. Clone the repository and open it in **Android Studio**.
2. Let Gradle sync (Gradle 9.3.1 / AGP 9.1.1).
3. **Build** → **Generate Signed Bundle / APK**, or `gradle :app:assembleRelease`.
4. Output: `app/build/outputs/apk/release/app-release.apk`.

---

## ⚡ Performance notes

The release build is tuned for smooth playback of streamed answers on mid/low-end phones:

- R8 + resource shrinking are enabled for `release`.
- Streamed tokens are batched (~25 fps) instead of recomposing per token, and the
  assistant bubble recomposes in its own scope.
- Markdown/syntax highlighting patterns are pre-compiled; inline markdown is cached.
- Large launcher artwork is stored as WebP; the unused Firebase/AppCheck stack is gone.
- The HTTP layer speaks OkHttp + Moshi directly, so the unused Retrofit, Moshi
  converter and request-logging interceptor are no longer packaged.
- The API key is decrypted from the AndroidKeyStore once per process, not per request.

## Provider and free-tier notes

The catalog provides connection profiles, not free credits. Each provider controls its own free quota, eligibility, region, model availability, rate limits, and billing terms; verify those terms on the provider's official dashboard before use. HCNSEC remains the default route and no provider key is required unless you choose to activate that provider.

All provider keys are entered by the user in the app and stored locally. The app does not proxy requests through a third-party server. For custom endpoints, use HTTPS and include the API's version path (usually `/v1`).

# Build outputs are published as a GitHub Release asset plus a git blob.
