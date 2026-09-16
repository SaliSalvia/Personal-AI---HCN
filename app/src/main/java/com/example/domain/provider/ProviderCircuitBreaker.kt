package com.example.domain.provider

/** Circuit breaker states. */
enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

/** Observable circuit breaker state for the registry / API Center. */
data class CircuitBreakerSnapshot(
    val state: CircuitState,
    val consecutiveFailures: Int,
    val openedAtMillis: Long?,
) {
    val isOpen: Boolean get() = state == CircuitState.OPEN
}

/**
 * Provider circuit breaker.
 *
 * The repository contained no circuit breaker before Phase 4.2, so this is the single
 * implementation used by all providers (Phase 4.2 §9). Behaviour:
 *
 * - transient failures ([ProviderErrorPolicy.countsTowardCircuit]) increment the failure count;
 * - [failureThreshold] consecutive transient failures open the circuit;
 * - while OPEN, requests are refused without touching the network;
 * - after [openDurationMillis] the circuit becomes HALF_OPEN and allows exactly one probe;
 * - a successful probe closes the circuit and resets the counters;
 * - a failed probe re-opens it for another cooldown period;
 * - authentication failures and quota exhaustion never open the circuit, so a single
 *   non-transient failure can never permanently disable a provider.
 */
class ProviderCircuitBreaker(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val openDurationMillis: Long = DEFAULT_OPEN_DURATION_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val DEFAULT_FAILURE_THRESHOLD: Int = 3
        const val DEFAULT_OPEN_DURATION_MILLIS: Long = 30_000L
    }

    private val lock = Any()
    private var state: CircuitState = CircuitState.CLOSED
    private var consecutiveFailures: Int = 0
    private var openedAtMillis: Long? = null
    private var probeInFlight: Boolean = false
    private var probeStartedAtMillis: Long? = null

    init {
        require(failureThreshold >= 1) { "failureThreshold must be >= 1" }
        require(openDurationMillis >= 0) { "openDurationMillis must be >= 0" }
    }

    /** Current state, applying the time-based OPEN -> HALF_OPEN transition. */
    fun currentState(): CircuitState = synchronized(lock) {
        refreshLocked()
        state
    }

    /**
     * Returns true when a request may be attempted.
     *
     * In HALF_OPEN exactly one probe is admitted; concurrent callers are refused until the probe
     * completes (or is abandoned through [abandonProbe]).
     */
    fun allowRequest(): Boolean = synchronized(lock) {
        refreshLocked()
        when (state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> false
            CircuitState.HALF_OPEN -> {
                if (probeInFlight) {
                    false
                } else {
                    probeInFlight = true
                    probeStartedAtMillis = clock()
                    true
                }
            }
        }
    }

    /** Records a successful request; closes the circuit and resets counters. */
    fun recordSuccess() {
        synchronized(lock) {
            state = CircuitState.CLOSED
            consecutiveFailures = 0
            openedAtMillis = null
            probeInFlight = false
            probeStartedAtMillis = null
        }
    }

    /** Records a normalized failure. Non-transient kinds are ignored by design. */
    fun recordFailure(kind: ProviderErrorKind) {
        if (!ProviderErrorPolicy.countsTowardCircuit(kind)) return

        synchronized(lock) {
            if (state == CircuitState.HALF_OPEN) {
                // The probe failed: go straight back to OPEN for another cooldown.
                state = CircuitState.OPEN
                openedAtMillis = clock()
                consecutiveFailures = maxOf(consecutiveFailures + 1, failureThreshold)
                probeInFlight = false
                probeStartedAtMillis = null
                return
            }

            consecutiveFailures += 1
            if (consecutiveFailures >= failureThreshold) {
                state = CircuitState.OPEN
                openedAtMillis = clock()
                probeInFlight = false
                probeStartedAtMillis = null
            }
        }
    }

    /**
     * Releases a HALF_OPEN probe that never produced an outcome (for example because the caller
     * cancelled). Without this, a cancelled probe would block the circuit forever.
     */
    fun abandonProbe() {
        synchronized(lock) {
            if (state == CircuitState.HALF_OPEN) {
                probeInFlight = false
                probeStartedAtMillis = null
            }
        }
    }

    /** Snapshot for the registry / API Center. */
    fun snapshot(): CircuitBreakerSnapshot = synchronized(lock) {
        refreshLocked()
        CircuitBreakerSnapshot(
            state = state,
            consecutiveFailures = consecutiveFailures,
            openedAtMillis = openedAtMillis,
        )
    }

    private fun refreshLocked() {
        if (state != CircuitState.OPEN) {
            // Guard against a probe that was admitted but never resolved.
            if (state == CircuitState.HALF_OPEN && probeInFlight) {
                val startedAt = probeStartedAtMillis
                if (startedAt != null && clock() - startedAt >= openDurationMillis) {
                    probeInFlight = false
                    probeStartedAtMillis = null
                }
            }
            return
        }

        val openedAt = openedAtMillis ?: return
        if (clock() - openedAt >= openDurationMillis) {
            state = CircuitState.HALF_OPEN
            probeInFlight = false
            probeStartedAtMillis = null
        }
    }
}
