# SALi-HCNSEC

Personal AI Assistant powered exclusively by your **HCNSEC API** (`https://api.hcnsec.cn/v1`). Built with modern Android, Jetpack Compose, Kotlin Coroutines & Flow, and Android KeyStore security.

---

## ✨ Features
- **Exclusively HCNSEC Powered:** Direct streaming integration with official OpenAI-compatible endpoint `https://api.hcnsec.cn/v1/chat/completions`.
- **Reasoning Process Display:** Collapsible visual accordion for DeepSeek-R1 / HCNSEC reasoning tokens (`reasoning_content`).
- **ZIP Workspace & Codebase Analysis:** Upload ZIP files directly to extract code trees and conduct holistic architectural discussions.
- **Hardware-Backed KeyStore Security:** User API keys are securely encrypted on-device with AES-GCM and MasterKeys.
- **Export & Regeneration:** One-click Markdown export to clipboard, response regeneration, and copyable code blocks with language indicators.
- **Zero-Bug Material 3 Design:** Edge-to-edge support, custom dark theme (`#0B0C10`, `#14151F`, `#8B5CF6`), and smooth auto-scrolling.

---

## 🧩 Provider Architecture (Phase 4.2 — HCNSEC)

HCNSEC is integrated through a provider-neutral architecture rather than a bespoke client:

```
UI → repositories → HcnsecProviderGateway → ProviderRegistry → HcnsecProviderAdapter → HTTPS → api.hcnsec.cn
```

- **Single source of truth** — `ProviderRegistry` owns provider identity, enabled/configured state,
  capability metadata, health and circuit-breaker state.
- **Credential boundary** — provider code depends on `ProviderCredentialSource`; only
  `KeystoreManager`/`ApiKeyRepository` touch Android secure storage. Credentials are never logged,
  never placed in URLs, UI state, errors or snapshots.
- **Normalized errors** — authentication, invalid request, unavailable model, rate limit, quota
  exhaustion, timeout, network, server, malformed response, cancellation and unknown are distinct and
  never conflated.
- **Health + circuit breaker** — real outcomes drive `AVAILABLE`/`DEGRADED`/`NETWORK_ERROR`/
  `RATE_LIMITED`/`QUOTA_EXHAUSTED`/`AUTH_ERROR`/`DISABLED`, and repeated transient failures open a
  CLOSED/OPEN/HALF_OPEN circuit breaker. Authentication failures and quota exhaustion never open it.
- **Quota honesty** — UNKNOWN is modelled separately from ZERO; nothing is fabricated. The previous
  undocumented billing call has been removed.
- **No startup traffic** — opening the app or a screen never contacts the API; "Test Connection" is
  user-triggered with a strict timeout, and streaming is fully cancellable.
- **Streaming** — incremental deltas only, single completion event, normalized mid-stream errors, no
  duplicate final text.

Later-phase providers (Gemini, Groq, Mistral, You.com) are registered as reserved, disabled slots
without adapters. MCP, agents, sandboxes and file/research/memory engines are **not** part of this
phase.

📄 Documentation: [`AI_CONTEXT.md`](AI_CONTEXT.md) · [`docs/PHASE_4_2_HCNSEC.md`](docs/PHASE_4_2_HCNSEC.md)

### Verification

CI (`.github/workflows/build-apk.yml`) runs the unit tests and builds the debug APK.
Stage 4.2 shipped 130 new deterministic tests (fake transports, no network, no real credentials)
alongside the 10 pre-existing tests.

> **Live HCNSEC smoke test: NOT PERFORMED — NO SAFE CREDENTIAL AVAILABLE.** Request/response/error
> handling is verified structurally against fakes only; see the limitations section of
> `docs/PHASE_4_2_HCNSEC.md`. No claim of a real API call is made.

---

## 🚀 Building the APK

### Method 1: Direct Build via Google AI Studio
In the AI Studio interface:
1. Open the project settings menu or the export/download button.
2. Select **Generate APK / AAB**.
3. Download the compiled file directly to your device.

### Method 2: Automatic GitHub Actions (CI/CD)
When you push this repository to GitHub:
1. GitHub Actions will automatically trigger the `.github/workflows/build-apk.yml` workflow.
2. Once the build completes (usually ~2-3 minutes), go to the **Actions** tab on your GitHub repository.
3. Click on the latest workflow run and download the `Sali-HCNSEC-Debug-APK` zip file containing `app-debug.apk`.

### Method 3: Local Android Studio
1. Clone or download this repository.
2. Open the project folder in **Android Studio**.
3. Let Gradle sync dependencies.
4. Go to **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. The output APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`
