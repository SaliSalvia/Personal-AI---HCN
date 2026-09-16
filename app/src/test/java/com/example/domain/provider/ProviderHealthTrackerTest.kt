package com.example.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Health-state transitions required by Phase 4.2 §8.
 */
class ProviderHealthTrackerTest {

    private fun tracker(): ProviderHealthTracker = ProviderHealthTracker(clock = { 1_000L })

    @Test
    fun `starts unknown`() {
        val health = tracker()

        assertEquals(ProviderHealthState.UNKNOWN, health.snapshot.value.state)
        assertNull(health.snapshot.value.lastOutcomeAtMillis)
        assertEquals(0, health.snapshot.value.consecutiveFailures)
    }

    @Test
    fun `successful request marks the provider available`() {
        val health = tracker()

        health.recordSuccess(latencyMillis = 42L)

        assertEquals(ProviderHealthState.AVAILABLE, health.snapshot.value.state)
        assertEquals(42L, health.snapshot.value.lastLatencyMillis)
        assertEquals(1_000L, health.snapshot.value.lastOutcomeAtMillis)
        assertEquals(0, health.snapshot.value.consecutiveFailures)
    }

    @Test
    fun `transport failures map to network error`() {
        val health = tracker()

        health.recordFailure(ProviderErrors.network("down"))
        assertEquals(ProviderHealthState.NETWORK_ERROR, health.snapshot.value.state)

        health.recordFailure(ProviderErrors.timeout("slow"))
        assertEquals(ProviderHealthState.NETWORK_ERROR, health.snapshot.value.state)
        assertEquals(2, health.snapshot.value.consecutiveFailures)
    }

    @Test
    fun `server and malformed responses map to degraded`() {
        val health = tracker()

        health.recordFailure(ProviderErrors.serverError("boom"))
        assertEquals(ProviderHealthState.DEGRADED, health.snapshot.value.state)

        health.recordFailure(ProviderErrors.malformedResponse("bad json"))
        assertEquals(ProviderHealthState.DEGRADED, health.snapshot.value.state)
    }

    @Test
    fun `authentication failure maps to auth error`() {
        val health = tracker()

        health.recordFailure(ProviderErrors.authentication("rejected"))

        assertEquals(ProviderHealthState.AUTH_ERROR, health.snapshot.value.state)
    }

    @Test
    fun `rate limiting maps to rate limited without claiming quota exhaustion`() {
        val health = tracker()

        health.recordFailure(ProviderErrors.rateLimited("slow down"))

        assertEquals(ProviderHealthState.RATE_LIMITED, health.snapshot.value.state)
        assertEquals(false, health.snapshot.value.quota.exhausted)
    }

    @Test
    fun `quota exhaustion is recorded explicitly`() {
        val health = tracker()

        health.recordFailure(ProviderErrors.quotaExhausted("no quota"))

        assertEquals(ProviderHealthState.QUOTA_EXHAUSTED, health.snapshot.value.state)
        assertTrue(health.snapshot.value.quota.exhausted)
        assertTrue("an exhaustion signal must not invent an amount", health.snapshot.value.quota.remaining.isUnknown)
    }

    @Test
    fun `a later success clears a previous exhaustion flag`() {
        val health = tracker()

        health.recordFailure(ProviderErrors.quotaExhausted("no quota"))
        health.recordSuccess(latencyMillis = 10L)

        assertEquals(ProviderHealthState.AVAILABLE, health.snapshot.value.state)
        assertEquals(false, health.snapshot.value.quota.exhausted)
    }

    @Test
    fun `caller side failures do not downgrade provider health`() {
        val health = tracker()
        health.recordSuccess(latencyMillis = 5L)

        for (kind in listOf(
            ProviderErrorKind.INVALID_REQUEST,
            ProviderErrorKind.MODEL_UNAVAILABLE,
            ProviderErrorKind.NOT_CONFIGURED,
            ProviderErrorKind.CIRCUIT_OPEN,
            ProviderErrorKind.UNKNOWN,
        )) {
            health.recordFailure(ProviderError(kind, "caller side"))
            assertEquals(
                "kind $kind must not downgrade health",
                ProviderHealthState.AVAILABLE,
                health.snapshot.value.state,
            )
        }
    }

    @Test
    fun `cancellation is completely ignored`() {
        val health = tracker()
        health.recordSuccess(latencyMillis = 7L)
        val before = health.snapshot.value

        health.recordFailure(ProviderErrors.cancelled())

        assertEquals(before, health.snapshot.value)
        assertNull(health.snapshot.value.lastErrorMessage)
    }

    @Test
    fun `disabling and re-enabling the provider is reflected in health`() {
        val health = tracker()

        health.setEnabled(false)
        assertEquals(ProviderHealthState.DISABLED, health.snapshot.value.state)

        health.setEnabled(true)
        assertEquals(ProviderHealthState.UNKNOWN, health.snapshot.value.state)
    }

    @Test
    fun `known rate limit data survives an uninformative failure`() {
        val health = tracker()
        val known = ProviderRateLimitSnapshot(
            limitRequests = ProviderValue.Known(10),
            remainingRequests = ProviderValue.Known(0),
            source = RateLimitSource.RESPONSE_HEADERS,
            observedAtMillis = 1_000L,
        )

        health.recordSuccess(latencyMillis = 1L, rateLimit = known)
        health.recordFailure(ProviderErrors.network("down"))

        assertEquals(ProviderValue.Known(0), health.snapshot.value.rateLimit.remainingRequests)
    }

    @Test
    fun `reset returns to the initial state`() {
        val health = tracker()
        health.recordFailure(ProviderErrors.serverError("boom"))

        health.reset()

        assertEquals(ProviderHealthState.UNKNOWN, health.snapshot.value.state)
        assertNull(health.snapshot.value.lastErrorMessage)
    }
}
