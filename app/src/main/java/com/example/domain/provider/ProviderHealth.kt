package com.example.domain.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Normalized provider health states.
 *
 * These mirror the states required by Phase 4.2 §8. The existing repository had no health model,
 * so this is the single health vocabulary used by the registry, the adapters and the API Center.
 */
enum class ProviderHealthState {
    /** No request has been observed yet. Never a claim about reachability. */
    UNKNOWN,

    /** Last observed outcome was a success. */
    AVAILABLE,

    /** Provider reachable but returning server-side or malformed responses. */
    DEGRADED,

    /** Transport-level failure (DNS/TLS/connection/timeout). */
    NETWORK_ERROR,

    /** Provider throttled us (HTTP 429 without a quota signal). */
    RATE_LIMITED,

    /** Provider confirmed the quota is exhausted. */
    QUOTA_EXHAUSTED,

    /** Credential rejected. */
    AUTH_ERROR,

    /** Administratively disabled. */
    DISABLED,
}

/**
 * Immutable health snapshot.
 *
 * @param lastErrorMessage user-safe text only. Credentials can never reach this field because
 *   [ProviderError.redact] is applied on construction of every [ProviderError] and this snapshot
 *   only ever stores `ProviderError.message` (never diagnostics or headers).
 */
data class ProviderHealthSnapshot(
    val state: ProviderHealthState = ProviderHealthState.UNKNOWN,
    val consecutiveFailures: Int = 0,
    val lastOutcomeAtMillis: Long? = null,
    val lastLatencyMillis: Long? = null,
    val lastErrorMessage: String? = null,
    val rateLimit: ProviderRateLimitSnapshot = ProviderRateLimitSnapshot(),
    val quota: ProviderQuotaSnapshot = ProviderQuotaSnapshot(),
)

/**
 * Tracks the health of a single provider.
 *
 * Thread-safe: adapters record outcomes from IO dispatcher threads and the UI observes
 * [snapshot] from the main thread.
 */
class ProviderHealthTracker(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val _snapshot = MutableStateFlow(ProviderHealthSnapshot())

    /** Current health, observable by the API Center. */
    val snapshot: StateFlow<ProviderHealthSnapshot> = _snapshot.asStateFlow()

    /** Records a successful provider interaction. */
    fun recordSuccess(
        latencyMillis: Long,
        rateLimit: ProviderRateLimitSnapshot = ProviderRateLimitSnapshot(),
    ) {
        synchronized(lock) {
            _snapshot.value = _snapshot.value.copy(
                state = ProviderHealthState.AVAILABLE,
                consecutiveFailures = 0,
                lastOutcomeAtMillis = clock(),
                lastLatencyMillis = latencyMillis,
                lastErrorMessage = null,
                rateLimit = rateLimit,
                // A successful call proves the quota is not exhausted right now; numeric
                // quota information is still only what the provider actually reported.
                quota = _snapshot.value.quota.copy(exhausted = false, observedAtMillis = clock()),
            )
        }
    }

    /**
     * Records a normalized failure.
     *
     * Cancellation is deliberately ignored: a cancelled request must never be recorded as
     * quota exhaustion, authentication failure or provider outage (Phase 4.2 §6).
     */
    fun recordFailure(
        error: ProviderError,
        rateLimit: ProviderRateLimitSnapshot = ProviderRateLimitSnapshot(),
    ) {
        if (error.kind == ProviderErrorKind.CANCELLED) return

        synchronized(lock) {
            val previous = _snapshot.value
            val nextState = healthStateFor(error.kind, previous.state)
            val countsAsFailure = countsAsConsecutiveFailure(error.kind)

            _snapshot.value = previous.copy(
                state = nextState,
                consecutiveFailures = if (countsAsFailure) previous.consecutiveFailures + 1 else previous.consecutiveFailures,
                lastOutcomeAtMillis = clock(),
                lastLatencyMillis = previous.lastLatencyMillis,
                lastErrorMessage = error.message,
                rateLimit = if (rateLimit.isUnknown()) previous.rateLimit else rateLimit,
                quota = when (error.kind) {
                    ProviderErrorKind.QUOTA_EXHAUSTED ->
                        previous.quota.copy(exhausted = true, observedAtMillis = clock())

                    else -> previous.quota
                },
            )
        }
    }

    /** Marks the provider as administratively disabled (or clears that state). */
    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            _snapshot.value = if (enabled) {
                _snapshot.value.copy(state = ProviderHealthState.UNKNOWN)
            } else {
                _snapshot.value.copy(state = ProviderHealthState.DISABLED)
            }
        }
    }

    /** Resets to the initial unknown state. */
    fun reset() {
        synchronized(lock) {
            _snapshot.value = ProviderHealthSnapshot()
        }
    }

    private fun healthStateFor(kind: ProviderErrorKind, previous: ProviderHealthState): ProviderHealthState =
        when (kind) {
            ProviderErrorKind.AUTHENTICATION -> ProviderHealthState.AUTH_ERROR
            ProviderErrorKind.QUOTA_EXHAUSTED -> ProviderHealthState.QUOTA_EXHAUSTED
            ProviderErrorKind.RATE_LIMITED -> ProviderHealthState.RATE_LIMITED
            ProviderErrorKind.NETWORK, ProviderErrorKind.TIMEOUT -> ProviderHealthState.NETWORK_ERROR
            ProviderErrorKind.SERVER_ERROR, ProviderErrorKind.MALFORMED_RESPONSE -> ProviderHealthState.DEGRADED
            ProviderErrorKind.PROVIDER_DISABLED -> ProviderHealthState.DISABLED

            // Caller-side or non-diagnostic outcomes must not downgrade provider health:
            // a malformed request, an unavailable model, a missing credential or an open circuit
            // says nothing about whether the provider itself is healthy.
            ProviderErrorKind.INVALID_REQUEST,
            ProviderErrorKind.MODEL_UNAVAILABLE,
            ProviderErrorKind.NOT_CONFIGURED,
            ProviderErrorKind.CIRCUIT_OPEN,
            ProviderErrorKind.CANCELLED,
            ProviderErrorKind.UNKNOWN,
            -> previous
        }

    private fun countsAsConsecutiveFailure(kind: ProviderErrorKind): Boolean = when (kind) {
        ProviderErrorKind.NETWORK,
        ProviderErrorKind.TIMEOUT,
        ProviderErrorKind.SERVER_ERROR,
        ProviderErrorKind.MALFORMED_RESPONSE,
        ProviderErrorKind.AUTHENTICATION,
        ProviderErrorKind.RATE_LIMITED,
        ProviderErrorKind.QUOTA_EXHAUSTED,
        -> true

        else -> false
    }
}
