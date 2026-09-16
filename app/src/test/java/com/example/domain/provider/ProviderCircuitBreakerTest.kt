package com.example.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CLOSED / OPEN / HALF_OPEN behaviour, driven by an injectable clock so the suite is deterministic.
 */
class ProviderCircuitBreakerTest {

    @Test
    fun `starts closed and admits requests`() {
        val breaker = ProviderCircuitBreaker(clock = { 0L })

        assertEquals(CircuitState.CLOSED, breaker.currentState())
        assertTrue(breaker.allowRequest())
        assertEquals(0, breaker.snapshot().consecutiveFailures)
    }

    @Test
    fun `non transient failures never open the circuit`() {
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, clock = { 0L })

        breaker.recordFailure(ProviderErrorKind.AUTHENTICATION)
        assertEquals(CircuitState.CLOSED, breaker.currentState())

        breaker.recordFailure(ProviderErrorKind.QUOTA_EXHAUSTED)
        assertEquals(CircuitState.CLOSED, breaker.currentState())

        breaker.recordFailure(ProviderErrorKind.CANCELLED)
        assertEquals(CircuitState.CLOSED, breaker.currentState())

        assertTrue(breaker.allowRequest())
    }

    @Test
    fun `consecutive transient failures open the circuit`() {
        val breaker = ProviderCircuitBreaker(failureThreshold = 3, clock = { 0L })

        breaker.recordFailure(ProviderErrorKind.NETWORK)
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        assertEquals(CircuitState.CLOSED, breaker.currentState())

        breaker.recordFailure(ProviderErrorKind.TIMEOUT)

        assertEquals(CircuitState.OPEN, breaker.currentState())
        assertFalse("an open circuit must refuse requests", breaker.allowRequest())
        assertTrue(breaker.snapshot().isOpen)
    }

    @Test
    fun `cooldown moves the circuit to half open`() {
        var now = 0L
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, openDurationMillis = 1_000L, clock = { now })

        breaker.recordFailure(ProviderErrorKind.SERVER_ERROR)
        assertEquals(CircuitState.OPEN, breaker.currentState())

        now = 999L
        assertEquals(CircuitState.OPEN, breaker.currentState())

        now = 1_000L
        assertEquals(CircuitState.HALF_OPEN, breaker.currentState())
    }

    @Test
    fun `half open admits exactly one probe`() {
        var now = 0L
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, openDurationMillis = 100L, clock = { now })
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        now = 200L

        assertTrue("the first probe is admitted", breaker.allowRequest())
        assertFalse("concurrent callers must wait for the probe", breaker.allowRequest())
    }

    @Test
    fun `successful probe closes the circuit and resets counters`() {
        var now = 0L
        val breaker = ProviderCircuitBreaker(failureThreshold = 2, openDurationMillis = 100L, clock = { now })
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        now = 200L
        assertTrue(breaker.allowRequest())

        breaker.recordSuccess()

        assertEquals(CircuitState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.snapshot().consecutiveFailures)
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun `failed probe reopens the circuit for another cooldown`() {
        var now = 0L
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, openDurationMillis = 100L, clock = { now })
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        now = 200L
        assertTrue(breaker.allowRequest())

        breaker.recordFailure(ProviderErrorKind.NETWORK)

        assertEquals(CircuitState.OPEN, breaker.currentState())
        assertFalse(breaker.allowRequest())

        now = 299L
        assertEquals(CircuitState.OPEN, breaker.currentState())

        now = 300L
        assertEquals(CircuitState.HALF_OPEN, breaker.currentState())
    }

    @Test
    fun `abandoning a probe lets a later request probe again`() {
        var now = 0L
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, openDurationMillis = 100L, clock = { now })
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        now = 200L

        assertTrue(breaker.allowRequest())
        assertFalse(breaker.allowRequest())

        breaker.abandonProbe()

        assertTrue("a cancelled probe must not block recovery", breaker.allowRequest())
    }

    @Test
    fun `invalid configuration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ProviderCircuitBreaker(failureThreshold = 0) }
        assertThrows(IllegalArgumentException::class.java) { ProviderCircuitBreaker(openDurationMillis = -1L) }
    }

    @Test
    fun `authentication failure after repeated outages does not extend the outage`() {
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, clock = { 0L })
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        assertEquals(CircuitState.OPEN, breaker.currentState())

        breaker.recordFailure(ProviderErrorKind.AUTHENTICATION)

        assertEquals(CircuitState.OPEN, breaker.currentState())
        assertEquals(1, breaker.snapshot().consecutiveFailures)
    }
}
