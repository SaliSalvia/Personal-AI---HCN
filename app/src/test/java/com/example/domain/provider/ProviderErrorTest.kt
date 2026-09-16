package com.example.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error taxonomy, retry/failover eligibility and diagnostic redaction.
 */
class ProviderErrorTest {

    @Test
    fun `authentication and quota failures are never retryable`() {
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.AUTHENTICATION))
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.QUOTA_EXHAUSTED))
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.INVALID_REQUEST))
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.MODEL_UNAVAILABLE))
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.CANCELLED))
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.MALFORMED_RESPONSE))
    }

    @Test
    fun `transient transport failures are retryable`() {
        assertTrue(ProviderErrorPolicy.isRetryable(ProviderErrorKind.TIMEOUT))
        assertTrue(ProviderErrorPolicy.isRetryable(ProviderErrorKind.NETWORK))
        assertTrue(ProviderErrorPolicy.isRetryable(ProviderErrorKind.SERVER_ERROR))
        assertTrue(ProviderErrorPolicy.isRetryable(ProviderErrorKind.RATE_LIMITED))
    }

    @Test
    fun `failover eligibility is limited to unusable or transient providers`() {
        assertTrue(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.PROVIDER_DISABLED))
        assertTrue(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.NOT_CONFIGURED))
        assertTrue(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.CIRCUIT_OPEN))
        assertTrue(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.NETWORK))

        assertFalse(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.AUTHENTICATION))
        assertFalse(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.QUOTA_EXHAUSTED))
        assertFalse(ProviderErrorPolicy.isFailoverAllowed(ProviderErrorKind.CANCELLED))
    }

    @Test
    fun `only transient failures count towards the circuit breaker`() {
        assertTrue(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.NETWORK))
        assertTrue(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.TIMEOUT))
        assertTrue(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.SERVER_ERROR))

        assertFalse(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.AUTHENTICATION))
        assertFalse(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.QUOTA_EXHAUSTED))
        assertFalse(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.RATE_LIMITED))
        assertFalse(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.CANCELLED))
        assertFalse(ProviderErrorPolicy.countsTowardCircuit(ProviderErrorKind.INVALID_REQUEST))
    }

    @Test
    fun `errors carry the policy defaults`() {
        val auth = ProviderErrors.authentication("rejected", 401)
        assertEquals(ProviderErrorKind.AUTHENTICATION, auth.kind)
        assertFalse(auth.retryable)

        val network = ProviderErrors.network("down")
        assertTrue(network.retryable)
        assertTrue(network.failoverAllowed)
        assertNull(network.httpStatus)
    }

    @Test
    fun `diagnostics are redacted`() {
        // Obvious non-secret fixture: it must not resemble a real provider key format.
        val fakeFixtureValue = "unit-test-bearer-token-value-0001"
        val redacted = ProviderError.redact("Authorization: Bearer $fakeFixtureValue was rejected")

        assertNull(ProviderError.redact(null))
        assertFalse(redacted!!.contains(fakeFixtureValue))
        assertTrue(redacted.contains("redacted"))
    }

    @Test
    fun `redaction strips bearer tokens and key shaped strings`() {
        val fakeFixtureValue = "unit-test-bearer-token-value-0002"
        val bearer = ProviderError.redact("sent header Bearer $fakeFixtureValue to host")!!
        assertFalse(bearer.contains(fakeFixtureValue))

        val keyLike = ProviderError.redact("credential api_key-abcdef1234567890 was rejected")!!
        assertFalse(keyLike.contains("abcdef1234567890"))
    }

    @Test
    fun `redaction bounds the diagnostic length`() {
        val long = ProviderError.redact("x".repeat(2_000))!!
        assertTrue(long.length <= 400)
    }

    @Test
    fun `provider exception exposes the normalized kind`() {
        val exception = ProviderException(ProviderErrors.quotaExhausted("no quota"))

        assertEquals(ProviderErrorKind.QUOTA_EXHAUSTED, exception.kind)
        assertEquals("no quota", exception.message)
        assertNull(exception.cause)
        assertTrue(exception.error.toString().contains("QUOTA_EXHAUSTED"))
    }

    @Test
    fun `factory helpers never include credentials`() {
        val errors = listOf(
            ProviderErrors.authentication("a"),
            ProviderErrors.invalidRequest("b"),
            ProviderErrors.modelUnavailable("c"),
            ProviderErrors.rateLimited("d"),
            ProviderErrors.quotaExhausted("e"),
            ProviderErrors.timeout("f"),
            ProviderErrors.network("g"),
            ProviderErrors.serverError("h"),
            ProviderErrors.malformedResponse("i"),
            ProviderErrors.cancelled(),
            ProviderErrors.disabled(ProviderId.HCNSEC),
            ProviderErrors.notConfigured(ProviderId.HCNSEC, "no credential is configured."),
            ProviderErrors.circuitOpen(ProviderId.HCNSEC),
            ProviderErrors.unknown("j"),
        )

        for (error in errors) {
            assertFalse(error.message.contains("Bearer "))
            assertFalse(error.message.contains("sk-"))
        }
    }
}
