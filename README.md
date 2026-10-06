# SALi-HCNSEC

Provider-neutral personal AI workspace. Connect **several providers at once** (Google AI Studio, HCNSEC, Groq, OpenRouter, and more) and use **all of their models** from one agent. Built with modern Android, Jetpack Compose, Kotlin Coroutines & Flow, and Android KeyStore security.

---

## ✨ Features
- **Simultaneous multi-provider AI:** Add any number of API keys side by side — Google AI Studio/Gemini, HCNSEC, Groq, OpenRouter, Cerebras, SambaNova, Together AI, DeepInfra, Fireworks, Mistral, Cohere, NVIDIA NIM, Hugging Face, and OpenAI. There is no single "active" provider: every configured API stays live. Most profiles use the shared OpenAI-compatible contract, while Gemini uses its native streaming API.
- **Custom provider endpoints:** Add any HTTPS OpenAI-compatible base URL and optional model from Settings. This supports self-hosted gateways and new providers without an app update.
- **Aggregated, hierarchical model picker:** Models are loaded from every configured provider's `/models` endpoint, tagged with their provider, and shown in a provider → model dropdown. Manual model IDs remain available for endpoints without a catalog.
- **Best-model auto routing across providers:** In auto mode the agent classifies each request and picks the strongest model from any connected API, then streams through the provider that owns it.
- **Reasoning Process Display:** Collapsible visual accordion for DeepSeek-R1 / HCNSEC reasoning tokens (`reasoning_content`).
- **Durable ZIP Workspace & Deep Analysis:** Every accepted ZIP is copied into app-private storage, indexed as an independent project version, protected with Zip Slip/size limits, and fingerprinted with SHA-256. The provider that owns the selected model receives a bounded, labelled deep-analysis context containing the project tree, summaries, code, Markdown, PDF and DOCX extracts when you explicitly ask for analysis; the original archive remains reusable offline until deleted.
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

The catalog provides connection profiles, not free credits. Each provider controls its own free quota, eligibility, region, model availability, rate limits, and billing terms; verify those terms on the provider's official dashboard before use. No provider key is required unless you choose to connect that provider, and any combination of providers can be connected at the same time.

All provider keys are entered by the user in the app and stored locally. The app does not proxy requests through a third-party server. For custom endpoints, use HTTPS and include the API's version path (usually `/v1`). Uploaded ZIPs are retained only in the app's private storage and are not silently uploaded or permanently stored by an AI provider; each requested analysis sends only the bounded extracted context to the provider that owns the selected model.

# Build outputs are published as a GitHub Release asset plus a git blob.
