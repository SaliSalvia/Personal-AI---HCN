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
