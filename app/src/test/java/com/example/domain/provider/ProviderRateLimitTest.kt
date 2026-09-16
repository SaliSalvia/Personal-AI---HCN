package com.example.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rate-limit / quota handling.
 *
 * The critical property under test is that UNKNOWN stays distinguishable from ZERO, and that no
 * amount, limit or reset timestamp is ever invented.
 */
class ProviderRateLimitTest {

    private val now = 1_000L

    @Test
    fun `empty headers produce a snapshot that claims nothing`() {
        val snapshot = ProviderRateLimitParser.fromHeaders(emptyMap(), now)

        assertTrue(snapshot.isUnknown())
        assertEquals(RateLimitSource.NONE, snapshot.source)
        assertEquals(ProviderRateLimitSnapshot.UNKNOWN, snapshot)
    }

    @Test
    fun `unparseable headers stay unknown instead of guessing`() {
        val snapshot = ProviderRateLimitParser.fromHeaders(
            mapOf(
                "x-ratelimit-limit-requests" to "not-a-number",
                "x-ratelimit-remaining-requests" to "",
                "retry-after" to "tomorrow",
                "x-ratelimit-reset-requests" to "??",
            ),
            now,
        )

        assertTrue(snapshot.isUnknown())
        assertTrue(snapshot.limitRequests.isUnknown)
        assertTrue(snapshot.retryAfterMillis.isUnknown)
    }

    @Test
    fun `reported values are parsed case insensitively`() {
        val snapshot = ProviderRateLimitParser.fromHeaders(
            mapOf(
                "X-RateLimit-Limit-Requests" to "100",
                "x-ratelimit-remaining-requests" to "42",
                "X-RateLimit-Limit-Tokens" to "5000",
                "x-ratelimit-remaining-tokens" to "10",
            ),
            now,
        )

        assertEquals(ProviderValue.Known(100), snapshot.limitRequests)
        assertEquals(ProviderValue.Known(42), snapshot.remainingRequests)
        assertEquals(ProviderValue.Known(5000), snapshot.limitTokens)
        assertEquals(ProviderValue.Known(10), snapshot.remainingTokens)
        assertEquals(RateLimitSource.RESPONSE_HEADERS, snapshot.source)
        assertEquals(now, snapshot.observedAtMillis)
    }

    @Test
    fun `an explicit zero is not the same as unknown`() {
        val snapshot = ProviderRateLimitParser.fromHeaders(
            mapOf("x-ratelimit-remaining-requests" to "0"),
            now,
        )

        assertEquals(ProviderValue.Known(0), snapshot.remainingRequests)
        assertFalse(snapshot.remainingRequests.isUnknown)
        assertNotEquals(ProviderValue.Unknown, snapshot.remainingRequests)
        assertEquals("0", snapshot.remainingRequests.displayText())
    }

    @Test
    fun `unknown renders differently from zero`() {
        assertEquals("unknown", ProviderValue.Unknown.displayText())
        assertEquals("not reported", ProviderValue.Unknown.displayText(unknownLabel = "not reported"))
        assertEquals("0", ProviderValue.Known(0).displayText())
        assertTrue(ProviderValue.Unknown.isUnknown)
        assertTrue(ProviderValue.Known(0).isKnown)
    }

    @Test
    fun `relative resets are converted to absolute instants`() {
        val seconds = ProviderRateLimitParser.fromHeaders(mapOf("x-ratelimit-reset-requests" to "30"), now)
        assertEquals(ProviderValue.Known(now + 30_000L), seconds.resetAtMillis)

        val duration = ProviderRateLimitParser.fromHeaders(mapOf("x-ratelimit-reset-requests" to "6m0s"), now)
        assertEquals(ProviderValue.Known(now + 360_000L), duration.resetAtMillis)

        val millis = ProviderRateLimitParser.fromHeaders(mapOf("x-ratelimit-reset-requests" to "100ms"), now)
        assertEquals(ProviderValue.Known(now + 100L), millis.resetAtMillis)

        val hours = ProviderRateLimitParser.fromHeaders(mapOf("x-ratelimit-reset-requests" to "1h2m3s"), now)
        assertEquals(ProviderValue.Known(now + 3_723_000L), hours.resetAtMillis)
    }

    @Test
    fun `absolute epoch resets are recognised`() {
        val epochSeconds = ProviderRateLimitParser.fromHeaders(mapOf("x-ratelimit-reset-requests" to "1800000000"), now)
        assertEquals(ProviderValue.Known(1_800_000_000_000L), epochSeconds.resetAtMillis)

        val epochMillisValue = ProviderRateLimitParser.fromHeaders(
            mapOf("x-ratelimit-reset-requests" to "1800000000000"),
            now,
        )
        assertEquals(ProviderValue.Known(1_800_000_000_000L), epochMillisValue.resetAtMillis)
    }

    @Test
    fun `retry after delta seconds are honoured`() {
        val numeric = ProviderRateLimitParser.fromHeaders(
            mapOf("x-ratelimit-remaining-requests" to "5", "retry-after" to "12"),
            now,
        )
        assertEquals(ProviderValue.Known(12_000L), numeric.retryAfterMillis)
    }

    @Test
    fun `quota snapshots are unknown by default`() {
        val quota = ProviderQuotaSnapshot.UNKNOWN

        assertFalse(quota.exhausted)
        assertTrue(quota.remaining.isUnknown)
        assertTrue(quota.total.isUnknown)
        assertTrue(quota.resetAtMillis.isUnknown)
        assertEquals(ProviderQuotaSource.UNKNOWN, quota.source)
    }

    @Test
    fun `confirmed exhaustion never fabricates an amount`() {
        val quota = ProviderQuotaSnapshot.confirmedExhausted(observedAtMillis = now)

        assertTrue(quota.exhausted)
        assertEquals(ProviderQuotaSource.ERROR_PAYLOAD, quota.source)
        assertEquals(now, quota.observedAtMillis)
        assertTrue("remaining must stay unknown", quota.remaining.isUnknown)
        assertNotEquals("unknown must not be rendered as zero", ProviderValue.Known(0L), quota.remaining)
    }
}
