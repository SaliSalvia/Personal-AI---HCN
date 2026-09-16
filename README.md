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

## 🚀 Building the APK

### Method 1: Automatic GitHub Actions (recommended)
Every push to `main` or `arena/01a0abb1-personal-ai-hcn` runs `.github/workflows/build-apk.yml`, which:

1. Installs JDK 17 + the Android SDK packages on the runner.
2. Runs the unit tests (`:app:testDebugUnitTest`).
3. Builds a **minified, resource-shrunk, signed release APK** (`:app:assembleRelease`).
4. Verifies the signature with `apksigner` and uploads `Sali-HCNSEC-Release-APK`.

Download `app-release.apk` from the **Actions** tab (artifact) or from the GitHub
**Release** that the workflow publishes. A machine readable summary of the last
build (size, SHA-256, signature, log tail) is written to `ci/last-build.json`.

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
- The API key is decrypted from the AndroidKeyStore once per process, not per request.
