# Phase 4.2 — HCNSEC Real Provider Integration

Status report for the first real provider integration. Companion document: [`../AI_CONTEXT.md`](../AI_CONTEXT.md).

---

## 1. Scope actually delivered

| Brief requirement | Status |
| --- | --- |
| Real HCNSEC adapter inside the existing architecture | Done — `HcnsecProviderAdapter` implements `ProviderAdapter` |
| Use `https://api.hcnsec.cn/v1` + `POST /chat/completions` | Done — single definition in `HcnsecProviderConfig`, asserted by test |
| Bearer auth from the secure credential layer only | Done — `ProviderCredentialSource` abstraction; no Android storage access in provider code |
| Configurable model identifiers | Done — request → configured default → configured list; `"auto"` never transmitted |
| System/user messages, temperature, max output tokens | Done |
| Normalized request/response, no provider leakage | Done — wire DTOs confined to `data/api/hcnsec`, asserted by test |
| Streaming with the neutral abstraction | Implemented and unit-tested with fakes (see limitation L1) |
| Timeouts + cancellation | Done — socket-level per-call deadline, cancellation never recorded as a provider fault |
| Error normalization (11 required categories + more) | Done |
| Health wiring | Done |
| Circuit breaker integration (single implementation) | Done |
| Rate-limit/quota handling with UNKNOWN ≠ ZERO | Done — `ProviderValue` sealed type |
| Model configuration without hard-coding one model | Done |
| Registry integration, other providers untouched | Done — 4 reserved disabled slots |
| API Center shows configured/enabled/health/model/masked key | Done |
| User-triggered "Test Connection", strict timeout | Done — never called automatically |
| Minimum real chat path UI → registry → adapter → API | Done |
| Secret-leakage audit | Done — automated + manual |
| Tests | 130 new tests, no network, no real credential |
| No startup API call / no quota consumed by opening the app | Done — removed the `init`-time model refresh |

### Deliberately not implemented (later phases)

Gemini, Groq, Mistral, You.com adapters, MCP, agent orchestration/loops, coding agent, sandbox
execution, file/ZIP intelligence, research engine, memory, invention lab, document/artifact engine,
billing, authentication, backend, cloud database.

---

## 2. Files added

Provider-neutral core (`com/example/domain/provider/`):

* `ProviderContract.kt` — request/response/stream/usage/models + `ProviderAdapter` contract
* `ProviderError.kt` — taxonomy, retry/failover policy, diagnostic redaction
* `ProviderHealth.kt` — health states + thread-safe tracker
* `ProviderCircuitBreaker.kt` — CLOSED / OPEN / HALF_OPEN
* `ProviderRateLimit.kt` — `ProviderValue` (UNKNOWN vs ZERO), rate-limit/quota snapshots, header parser
* `ProviderRegistry.kt` — single source of truth, gating, routing metadata, status snapshots
* `TestProviderConnection.kt` — user-triggered probe use case

Infrastructure (`com/example/data/`):

* `network/ProviderHttpTransport.kt` + `network/OkHttpProviderTransport.kt` — HTTPS-only transport seam
* `security/ProviderCredentialSource.kt` — credential abstraction + secure-storage bridge
* `api/hcnsec/HcnsecProviderConfig.kt` — endpoint + model configuration
* `api/hcnsec/HcnsecWireModels.kt` — HCNSEC wire DTOs (relocated, `UserBalanceDto` removed)
* `api/hcnsec/HcnsecProviderAdapter.kt` — the real adapter
* `api/hcnsec/HcnsecProviderGateway.kt` — application-facing entry point

Tests (130 new): `HcnsecProviderAdapterTest` (45), `ProviderHealthTrackerTest` (13),
`TestProviderConnectionTest` (12), `ProviderRegistryTest` (11), `SecretLeakageAuditTest` (11),
`ProviderErrorTest` (10), `ProviderCircuitBreakerTest` (10), `ProviderRateLimitTest` (10),
`ProviderBoundaryTest` (8), plus shared fakes.

## 3. Files changed

* `di/AppContainer.kt` — registry, adapter, transport, credential source, gateway wiring
* `data/repository/ChatRepository.kt` — provider-neutral messages/events
* `data/repository/ModelRepository.kt` — model catalogue through the gateway
* `ui/screens/settings/SettingsViewModel.kt` — provider status, user-triggered test, no init network call
* `ui/screens/settings/SettingsScreen.kt` — provider status card replacing the balance card
* `ui/screens/onboarding/OnboardingViewModel.kt` — ephemeral-credential validation, input cleared after storing
* `ui/screens/chat/ChatViewModel.kt` / `ChatScreen.kt` — user-triggered model refresh instead of `init`
* `ui/navigation/AppNavGraph.kt` — factories use the gateway
* `data/security/ApiKeyRepository.kt` — **security fix**: plaintext fallback removed, legacy plaintext purged
* `ui/screens/onboarding/OnboardingScreen.kt` — endpoint copy references the config constant
* `.github/workflows/build-apk.yml` — Gradle provisioned explicitly (the repo ships no wrapper jar), JDK 21, unit tests + APK build
* `README.md`, `AI_CONTEXT.md`, this document

## 4. Files removed

* `data/api/HcnsecApiClient.kt` — superseded by the adapter + gateway
* `data/api/HcnsecApiFactory.kt`, `data/api/HcnsecApiService.kt` — dead Retrofit path (never called) whose interceptor retried 401/429, which this phase forbids
* `data/api/HcnsecApiModels.kt` — replaced by the relocated wire models
* `getAccountUsage()` and its balance UI — undocumented billing endpoint, and quota must not be fabricated

---

## 5. Verification

Executed in CI (JDK 21 + Android SDK + Gradle 9.3.1): `:app:testDebugUnitTest` (all 140 tests) and
`:app:assembleDebug`.

**First green run:**
[35158466504](https://github.com/SaliSalvia/Personal-AI---HCN/actions/runs/35158466504) at commit
`3550634` — every step passed and both artifacts (unit-test report, `Personal-AI-Debug-APK`) were
uploaded. Each later push to this branch re-runs the same gate, and the workflow posts a per-commit
CI summary (test totals; failing assertions and compiler errors when the build breaks) as a comment on
the pull request.

**What the first CI rounds caught** — all fixed and re-verified:

1. five stream-failure sites emitted the wrapping `ProviderException` where `ProviderStreamEvent.Failed`
   requires a `ProviderError`; the recording helper is now split into a returned error and a thrown
   exception;
2. the repository-root helper in `SourceAudit` resolved to the module directory, because this Android
   layout has its own `app/.gitignore` that shadows the root one — it now walks up to
   `settings.gradle.kts`;
3. the TLS audit flagged a KDoc comment that *documented* the absence of a TLS bypass; audits now
   inspect comment-free code (and the comment was reworded);
4. one rate-limit assertion compared a relative `x-ratelimit-reset-requests` duration against an
   absolute instant instead of the recorded observation time.

Workflow defects were found and fixed along the way — all self-inflicted, none affecting app code:
`setup-gradle` was asked to restore a cache it could not; an inserted step briefly absorbed the
artifact-upload step's keys, which made the workflow file invalid; and the CI summary step tripped
over GitHub's `bash -eo pipefail` default, where `grep … | wc -l` exits non-zero on a *passing* test
run that simply has no `<failure` elements to match.

**Not executed in the authoring environment:** this sandbox has no JDK, no Android SDK and no
Gradle, and network egress is limited to GitHub — Maven Central, `dl.google.com` and
`services.gradle.org` are unreachable, so `compile`/`test`/`lint`/`assemble` cannot run locally.
Nothing in this document claims a local build, local test run, Android export or live API call.

### Live HCNSEC smoke test

```
LIVE SMOKE TEST: NOT PERFORMED — NO SAFE CREDENTIAL AVAILABLE
```

No HCNSEC credential exists in the environment, and a credential must never be pasted into chat or
committed. The end-to-end path is verified structurally (exact URL, method, headers, body and
response/error normalization) with fakes; a live run requires a configured credential on a device or
in an environment that supplies one through the secure layer.

## 6. Security audit

* No credential literal in main or test sources (automated regex audit over the source tree).
* `Authorization` is constructed in exactly one place; `api.hcnsec.cn` appears in exactly one source
  file — both enforced by tests.
* No `Log.`/`println`/`System.out` in provider, adapter or transport code; no HTTP logging
  interceptor anywhere.
* No TLS bypass (`sslSocketFactory`, `hostnameVerifier`, `checkServerTrusted`, `TrustAllCerts`),
  no plaintext `http://` endpoint, no cleartext-traffic relaxation in the manifest.
* No `.env` containing real credentials exists; `.env` is git-ignored.
* Plaintext credential fallback removed; a stored legacy plaintext value is purged rather than read.

## 7. Limitations

* **L1 — streaming not verified against the live service.** The SSE parser, termination, error and
  cancellation semantics are covered by deterministic fakes; behaviour against the real endpoint
  (chunk shapes, keepalives, duplicate `[DONE]`) is unverified in this environment.
* **L2 — no live request of any kind was made.** Request/response/error compatibility with the live
  API is unverified.
* **L3 — cancellation of a blocked read** depends on `Call.cancel()` plus the read timeout.
* **L4 — `ChatRepository` retains a last-resort model identifier** (`deepseek-chat`) used only when
  routing and configuration both yield nothing.
* **L5 — two of the brief's reserved provider slots are unknown** and were not invented; four
  later-phase providers are registered as disabled, adapter-less slots.
* **L6 — no coverage threshold exists** in this repository, so no enforced coverage comparison is
  possible; total tests increased from 10 to 140.
* **L7 — no Android export / APK was produced locally**; the APK is built in CI.
* No claim of zero bugs is made.
