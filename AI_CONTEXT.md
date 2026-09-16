# AI_CONTEXT — Personal AI Agent (SALi-HCNSEC)

_Last updated: Phase 4.2 — HCNSEC Real Provider Integration._

This document is the shared context for humans and AI agents working on this repository. It
describes what actually exists in the code, where things live, and — explicitly — what has and has
not been verified.

---

## 1. What this project actually is

An **Android application written in Kotlin with Jetpack Compose**, built with Gradle.

It is **not** a React Native / Expo / TypeScript project. There is no `package.json`, no
`src/services/`, and no TypeScript toolchain anywhere in the repository or its git history. Several
phase briefs refer to paths from a React Native project; §3 maps those names onto the real
structure so that the intent of each phase can still be followed.

Runtime stack:

| Concern | Implementation |
| --- | --- |
| UI | Jetpack Compose (Material 3), single Activity (`MainActivity`) |
| Navigation | `androidx.navigation.compose` (`AppNavGraph`) |
| Persistence | Room (`AppDatabase`, DAOs, entities) |
| Networking | OkHttp behind a provider-neutral transport seam |
| JSON | Moshi (codegen + reflective fallback) |
| DI | Manual composition root (`AppContainer`) |
| Secure storage | AndroidKeyStore AES-GCM (`KeystoreManager`, `ApiKeyRepository`) |
| Tests | JUnit4, Robolectric, Roborazzi, kotlinx-coroutines-test |

---

## 2. Phase status

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Foundation (app shell, theme, navigation) | Present in `main` |
| 2 | Android app / Compose UI shell | Present in `main` |
| 2.5 / 2.6 | Premium UI, tests | Partially present (`MarkdownRenderer`, `ChatInputBar`, Room, screenshot test) |
| 3 | Provider architecture | **Did not exist before Phase 4.2** — there was no provider contract, registry, health model, error taxonomy, quota model, routing metadata, failover policy or circuit breaker. Phase 4.2 introduces the minimal version of each. |
| 4.1 | Secure credential storage | Present: `KeystoreManager` (AES-GCM, hardware-backed) + `ApiKeyRepository` |
| **4.2** | **HCNSEC real provider integration** | **Implemented in this change set** (see §4–§11) |

---

## 3. Path mapping (brief → this repository)

The phase briefs describe a React Native app. This table maps each referenced path onto the real
Kotlin location so that the *intent* of the brief is preserved.

| Brief path | Actual location | Notes |
| --- | --- | --- |
| `src/services/providers/adapter.ts` | `com/example/domain/provider/ProviderContract.kt` + `com/example/data/api/hcnsec/HcnsecProviderAdapter.kt` | contract + real HCNSEC adapter |
| `src/services/providers/registry.ts` | `com/example/domain/provider/ProviderRegistry.kt` | single source of truth |
| `src/services/providers/health.ts` | `com/example/domain/provider/ProviderHealth.kt` | health states + tracker |
| `src/services/providers/errors.ts` | `com/example/domain/provider/ProviderError.kt` | taxonomy + retry/failover policy + redaction |
| `src/services/providers/quota.ts` | `com/example/domain/provider/ProviderRateLimit.kt` | `ProviderValue` (UNKNOWN vs ZERO), rate-limit + quota snapshots |
| `src/services/providers/circuit-breaker.ts` | `com/example/domain/provider/ProviderCircuitBreaker.kt` | CLOSED / OPEN / HALF_OPEN |
| `src/services/providers/routing.ts` / `failover.ts` | `ProviderRegistry.candidates(...)` + `ProviderErrorPolicy` | routing metadata + failover eligibility (deliberately minimal; no agent/router is built in this phase) |
| `src/services/providers/configuration.ts` | `com/example/data/api/hcnsec/HcnsecProviderConfig.kt` | HTTPS-only base URL + model configuration |
| `src/services/credentials/` | `com/example/data/security/` | `ApiKeyRepository`, `KeystoreManager`, `ProviderCredentialSource` |
| `src/native/secureCredentialStore.ts` | `com/example/data/security/KeystoreManager.kt` | AndroidKeyStore AES-GCM |
| Setup / onboarding UI | `com/example/ui/screens/onboarding/*` | first-run credential entry |
| Setup gate | `AppNavGraph` start destination via `apiKeyRepository.hasApiKey()` | routes to onboarding when no credential is stored |
| API Center | `com/example/ui/screens/settings/*` | credential + provider status surface |

The npm-based quality gate referenced by the brief (`npm ci`, `typecheck`, `lint`, `format:check`,
`test:ci`, `validate`, `expo export`) does not exist and cannot be created for a Gradle project. The
Gradle equivalents are documented in §12.

---

## 4. Request path (implemented)

```
Compose UI  (ChatScreen / SettingsScreen)
     ↓
ViewModel   (ChatViewModel / SettingsViewModel)   — no provider types
     ↓
Repository  (ChatRepository / ModelRepository)    — provider-neutral request only
     ↓
HcnsecProviderGateway                             — application entry point, no provider logic
     ↓
ProviderRegistry                                  — provider identity, gating, health, breaker
     ↓
HcnsecProviderAdapter                             — the only code that knows the HCNSEC wire format
     ↓
ProviderHttpTransport (OkHttp)                    — HTTPS only, strict timeouts, no logging
     ↓
https://api.hcnsec.cn/v1/chat/completions
```

`ProviderChatRequest` / `ProviderChatResponse` / `ProviderStreamEvent` / `ProviderError` are the only
types that cross the adapter boundary. HCNSEC wire DTOs live in
`com/example/data/api/hcnsec/HcnsecWireModels.kt` and a test asserts they never leak outside that
package.

---

## 5. HCNSEC integration

* **Base URL:** `https://api.hcnsec.cn/v1` (single definition — `HcnsecProviderConfig.DEFAULT_BASE_URL`;
  a test asserts no other source file contains the host).
* **Chat:** `POST /chat/completions`, OpenAI-compatible body (`model`, `messages`, `stream`,
  `temperature`, `max_tokens`); nullable fields are omitted when unset.
* **Models:** `GET /models` (pre-existing, OpenAI-compatible endpoint) for the model catalogue.
* **Auth:** `Authorization: Bearer <credential>`, built in exactly one place.
* **Model configuration:** the identifier comes from request → configured default model → configured
  list. The router sentinel `"auto"` is never transmitted as a model id, and no model is invented:
  with nothing configured the adapter fails with a normalized `NOT_CONFIGURED` error.
* **Capabilities advertised:** `CHAT_COMPLETION`, `STREAMING`, `SYSTEM_MESSAGES`, `TEMPERATURE`,
  `MAX_OUTPUT_TOKENS`, `MODEL_LISTING`, `REASONING_CONTENT`, `CONNECTION_TEST`.

### Streaming

`ProviderStreamEvent` carries `Content`, `Reasoning`, `Completed` and `Failed`.

* incremental deltas only — `Completed` carries **no text**, so the final answer cannot be appended
  twice;
* termination on `data: [DONE]` or end of stream, with exactly one `Completed` event;
* malformed chunks are skipped; a stream that yields *only* malformed chunks fails with
  `MALFORMED_RESPONSE`;
* mid-stream error payloads are normalized (quota/rate-limit/auth detection);
* cancellation propagates as `CancellationException` — never as a failure event — and aborts the
  in-flight HTTP call through a job-completion hook (`Call.cancel()` from the cancelling thread);
* no aggregation buffer is kept in the adapter (no unbounded memory growth).

### Timeouts and cancellation

* Connect 15 s, read 120 s, write 30 s; whole-call deadline 120 s for non-streaming requests.
* `ProviderChatRequest.timeoutMillis` sets a per-call deadline at the **socket** level — a coroutine
  timeout alone cannot interrupt a blocking read. `TestProviderConnection` uses this for its strict
  probe deadline and keeps a `withTimeout` backstop.
* Cancellation is recorded as neither quota exhaustion, authentication failure nor outage: health
  and the circuit breaker ignore it, and the breaker releases a half-open probe
  (`abandonProbe()`).

---

## 6. Credential boundary

* `KeystoreManager` + `ApiKeyRepository` are the **only** classes that touch Android secure storage.
* Provider code depends on `ProviderCredentialSource`; `StoredApiKeyCredentialSource` is the single
  bridge and lives in the security package.
* `ProviderCredential.toString()` is redacted, so accidental interpolation cannot leak a token.
* The credential is attached only inside `HcnsecProviderAdapter.buildHeaders(...)`; API tests assert
  it appears in no URL, no body, no error message, no diagnostic and no health snapshot.
* First-run onboarding validates an **unsaved** credential (`validateEphemeralCredential`) that is
  held in memory for the call only; on success it is stored through the hardware-backed repository
  and dropped from UI state.
* **Security hardening in this phase:** the previous plaintext fallback (`PLAIN:` in
  `SharedPreferences`, used when the keystore was unavailable) has been removed —
  `saveApiKey` now returns `false` and stores nothing rather than writing an unencrypted secret.
  Legacy plaintext values are purged on read instead of being decrypted.

---

## 7. Error taxonomy

`ProviderErrorKind`: `AUTHENTICATION`, `INVALID_REQUEST`, `MODEL_UNAVAILABLE`, `RATE_LIMITED`,
`QUOTA_EXHAUSTED`, `TIMEOUT`, `NETWORK`, `SERVER_ERROR`, `MALFORMED_RESPONSE`, `CANCELLED`,
`PROVIDER_DISABLED`, `NOT_CONFIGURED`, `CIRCUIT_OPEN`, `UNKNOWN`.

Mapping (HCNSEC → normalized):

| Provider signal | Kind | Retryable | Failover |
| --- | --- | --- | --- |
| 401 / 403 (no model mention) | `AUTHENTICATION` | no | no |
| 403/404 mentioning a model | `MODEL_UNAVAILABLE` | no | no |
| 400 / 409 / 422 | `INVALID_REQUEST` | no | no |
| 402 | `QUOTA_EXHAUSTED` | no | no |
| 429 with quota markers | `QUOTA_EXHAUSTED` | no | no |
| 429 otherwise | `RATE_LIMITED` | yes | no |
| 408 / 504, socket timeout | `TIMEOUT` | yes | yes |
| DNS / TLS / connection reset | `NETWORK` | yes | yes |
| 5xx | `SERVER_ERROR` | yes | yes |
| unparseable body / no choices | `MALFORMED_RESPONSE` | no | no |
| coroutine cancellation | `CANCELLED` | no | no |
| missing credential / model | `NOT_CONFIGURED` | no | yes |
| breaker open | `CIRCUIT_OPEN` | no | yes |

Diagnostics are passed through `ProviderError.redact(...)`, which strips bearer tokens and
key-shaped strings and caps length. The adapter performs **no retries** itself.

---

## 8. Health model

| Outcome | State |
| --- | --- |
| success | `AVAILABLE` |
| network failure / timeout | `NETWORK_ERROR` |
| 5xx / malformed response | `DEGRADED` |
| 429 (no quota signal) | `RATE_LIMITED` |
| confirmed quota exhaustion | `QUOTA_EXHAUSTED` |
| 401/403 | `AUTH_ERROR` |
| disabled in the registry | `DISABLED` |
| caller-side errors (invalid request, model unavailable, not configured, circuit open, unknown) | **state unchanged** — they say nothing about provider health |
| cancellation | **ignored entirely** |

`ProviderHealthTracker` is thread-safe (`MutableStateFlow` + lock) and exposes a `StateFlow` for the
API Center.

---

## 9. Circuit breaker

Single implementation (`ProviderCircuitBreaker`), injectable clock, deterministic tests.

* only transient kinds (`NETWORK`, `TIMEOUT`, `SERVER_ERROR`) count towards opening;
* 3 consecutive transient failures → `OPEN`; requests are then refused **without** touching the
  network (`CIRCUIT_OPEN`);
* after a 30 s cooldown the circuit reports `HALF_OPEN` and admits exactly one probe;
* a successful probe closes and resets; a failed probe re-opens for another cooldown;
* `abandonProbe()` releases a probe that was cancelled;
* authentication failures and quota exhaustion never open the circuit, so one non-transient failure
  cannot permanently disable the provider.

---

## 10. Rate limits and quota

`ProviderValue<T>` is a sealed type: `Unknown` | `Known(value)`. **UNKNOWN and ZERO are distinct**,
and `displayText()` renders them differently ("unknown" vs "0"). A test asserts this explicitly.

* `ProviderRateLimitParser` reads the conventional `x-ratelimit-*` headers plus `retry-after`
  (case-insensitive), supports delta-seconds, duration strings (`1s`, `100ms`, `6m0s`, `1h2m3s`) and
  epoch seconds/millis. Anything absent or unparseable stays `Unknown`.
* Confirmed exhaustion (`insufficient_quota`, quota markers, HTTP 402) sets
  `ProviderQuotaSnapshot.exhausted = true` **without** inventing an amount — `remaining` stays
  `Unknown`, deliberately not `0`.
* No amount, reset timestamp, billing figure or balance is ever fabricated.
* `HcnsecApiClient.getAccountUsage()` (which called the undocumented
  `GET /dashboard/billing/usage`) has been **removed**, along with the balance UI that displayed it.
  The API Center now shows only what the provider actually reports, and shows "unknown" otherwise.

---

## 11. API Center

`SettingsViewModel` exposes a secret-free `ProviderStatus`: provider name, configured, enabled,
health, circuit state, configured model, known model count, quota, rate limits, last latency and a
user-safe last error message. It is rendered in the Settings screen with an explicit note that
unknown values are never displayed as zero.

**No API call happens because the app or a screen opened.** `ChatViewModel` no longer refreshes the
model catalogue in `init` (it was doing so on every app launch); the refresh now runs only from
user actions. The connection test runs only when the user taps **Test**. A test asserts that
constructing the registry/gateway performs no provider call.

---

## 12. Quality gate for this repository

There is no npm gate. The Gradle equivalents are:

```bash
gradle :app:testDebugUnitTest     # unit tests (Kotlin/JVM + Robolectric)
gradle :app:assembleDebug         # compile + package the debug APK
gradle :app:lintDebug             # Android lint (not wired into CI)
```

CI (`.github/workflows/build-apk.yml`) provisions JDK 21, the Android SDK and Gradle 9.3.1 (the
repository ships no wrapper jar), then runs the unit tests and builds the debug APK. Test reports are
uploaded as artifacts.

**CI status: green.** Run
[35158466504](https://github.com/SaliSalvia/Personal-AI---HCN/actions/runs/35158466504) at commit
`3550634` completed every step: `:app:testDebugUnitTest` passed with no failures and
`:app:assembleDebug` produced the debug APK (`Personal-AI-Debug-APK` artifact). The workflow publishes
a per-commit **CI summary** comment on the pull request (test totals always; failing assertions and
compiler errors on failure), because job logs are not always reachable from tooling.

No coverage plugin (JaCoCo/Kover) is configured in this repository, so there is no coverage threshold
to reduce or compare — the test count is the only quantitative gate, and it grew from 10 to 140.

---

## 13. Tests

140 unit tests total: **130 new** provider/HCNSEC/security tests plus the 10 pre-existing tests.
All 140 execute in `:app:testDebugUnitTest` in CI (`gradle :app:testDebugUnitTest`, Robolectric included).

| Suite | Tests | Covers |
| --- | --- | --- |
| `HcnsecProviderAdapterTest` | 45 | construction, serialization, auth header, success/malformed/401/429/quota/400/404/timeout/DNS/TLS/5xx, health + breaker integration, rate-limit headers, streaming, cancellation, models, ephemeral credential, secret leakage |
| `ProviderErrorTest` | 10 | taxonomy, retry/failover/circuit policy, redaction |
| `ProviderHealthTrackerTest` | 13 | health transitions, cancellation immunity, quota signalling |
| `ProviderCircuitBreakerTest` | 10 | CLOSED/OPEN/HALF_OPEN, probe admission, recovery limits |
| `ProviderRateLimitTest` | 10 | UNKNOWN vs ZERO, header/duration/epoch parsing, no fabrication |
| `ProviderRegistryTest` | 11 | registration, duplicates, gating, routing metadata, reserved slots |
| `TestProviderConnectionTest` | 12 | probe minimality, strict timeout, normalized outcomes, no implicit calls |
| `SecretLeakageAuditTest` | 11 | credential-shaped literals, logging, TLS bypass, HTTPS-only, single endpoint/header site, `.env` hygiene |
| `ProviderBoundaryTest` | 8 | framework-free contract layer, credential abstraction, wire-type isolation, no startup calls, single definition site |

All provider/HCNSEC tests are deterministic pure-JVM tests using fake transports — **no network
access and no real credential**.

---

## 14. Phase boundary (what Phase 4.2 deliberately did not do)

Not implemented, by design: Gemini / Groq / Mistral / You.com adapters (registered as reserved,
disabled, adapter-less slots), MCP, agent orchestration or agent loops, coding agent, sandbox
execution, ZIP/file intelligence (pre-existing workspace code is untouched), research engine, memory
system, invention lab, document/artifact engine, billing, authentication, backend, cloud database.

---

## 15. Known limitations

1. **No live HCNSEC smoke test was performed** — no credential exists in the build environment, and
   credentials must never be pasted into chat or committed. Structural verification only.
2. **Streaming cancellation of a blocked read** relies on `Call.cancel()` from the cancelling thread
   plus the read timeout; a transport that ignores cancellation would only stop at the read timeout.
3. **`ChatRepository` keeps a last-resort model literal** (`deepseek-chat`) when neither routing nor
   configuration yields a model. It is a fallback identifier, not a credential, and remains
   documented here rather than silently changed.
4. **The brief's "other six provider slots" could not be reconstructed** — only the four
   later-phase providers named in the brief are reserved; the remaining two were deliberately not
   invented.
5. **No coverage threshold is configured** in this repository (no JaCoCo/kover plugin), so there is
   no enforced coverage number to compare against. Total suite size increased from 10 to 140 tests.
6. `domain.provider` types are not currently used by more than the HCNSEC path; later phases are
   expected to reuse them rather than introduce a second architecture.
