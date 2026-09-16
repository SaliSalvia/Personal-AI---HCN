package com.example.domain.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Test Connection" behaviour: user triggered only, strict timeout, normalized result, and no
 * credential exposure.
 */
class TestProviderConnectionTest {

    @Test
    fun `successful probe reports latency and the resolved model`() = runTest {
        val adapter = FakeProviderAdapter(latencyMillis = 25L, modelIds = listOf("model-a"))
        val useCase = TestProviderConnection(registry = registryWith(adapter, configuredModel = "model-a"))

        val result = useCase.execute(model = "model-a")

        assertTrue(result.success)
        assertEquals(25L, result.latencyMillis)
        assertEquals("model-a", result.model)
        assertNull(result.errorKind)
        assertEquals(1, adapter.chatCalls)
        assertFalse(result.message.contains("Bearer"))
    }

    @Test
    fun `probe is minimal so it cannot waste quota`() = runTest {
        val adapter = FakeProviderAdapter()
        val useCase = TestProviderConnection(registry = registryWith(adapter))

        useCase.execute(model = "model-a")

        val sent = adapter.lastRequest
        assertEquals("a probe must send exactly one message", 1, sent?.messages?.size)
        assertEquals(ProviderRole.USER, sent?.messages?.first()?.role)
        assertEquals(TestProviderConnection.PROBE_PROMPT, sent?.messages?.first()?.content)
        assertEquals(1, sent?.maxOutputTokens)
        assertEquals(0.0, sent?.temperature ?: -1.0, 0.0001)
        assertTrue(sent?.timeoutMillis != null)
    }

    @Test
    fun `authentication failure is normalized`() = runTest {
        val adapter = FakeProviderAdapter(failWith = ProviderErrors.authentication("HCNSEC rejected the API key."))
        val useCase = TestProviderConnection(registry = registryWith(adapter))

        val result = useCase.execute()

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.AUTHENTICATION, result.errorKind)
        assertFalse(result.message.contains("Bearer"))
    }

    @Test
    fun `quota exhaustion is normalized separately`() = runTest {
        val adapter = FakeProviderAdapter(failWith = ProviderErrors.quotaExhausted("no remaining quota"))
        val useCase = TestProviderConnection(registry = registryWith(adapter))

        val result = useCase.execute()

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.QUOTA_EXHAUSTED, result.errorKind)
    }

    @Test
    fun `cancellation is normalized separately from authentication quota and outage`() = runTest {
        val adapter = FakeProviderAdapter(failWith = ProviderErrors.cancelled())
        val useCase = TestProviderConnection(registry = registryWith(adapter))

        val result = useCase.execute()

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.CANCELLED, result.errorKind)
        assertEquals(
            "a cancelled request must not be reported as a provider fault",
            false,
            result.errorKind == ProviderErrorKind.AUTHENTICATION,
        )
    }

    @Test
    fun `hanging provider is cut off by the strict timeout`() = runTest {
        val adapter = FakeProviderAdapter(delayMillis = 60_000L)
        val useCase = TestProviderConnection(registry = registryWith(adapter), defaultTimeoutMillis = 1_000L)

        val result = useCase.execute(timeoutMillis = 1_000L)

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.TIMEOUT, result.errorKind)
        assertTrue(result.message.contains("timed out"))
    }

    @Test
    fun `unconfigured provider fails without any request`() = runTest {
        val adapter = FakeProviderAdapter()
        val useCase = TestProviderConnection(registry = registryWith(adapter, configured = false))

        val result = useCase.execute()

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.NOT_CONFIGURED, result.errorKind)
        assertEquals(0, adapter.chatCalls)
    }

    @Test
    fun `disabled provider fails without any request`() = runTest {
        val adapter = FakeProviderAdapter()
        val useCase = TestProviderConnection(registry = registryWith(adapter, enabled = false))

        val result = useCase.execute()

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.PROVIDER_DISABLED, result.errorKind)
        assertEquals(0, adapter.chatCalls)
    }

    @Test
    fun `open circuit prevents the probe from hitting the network`() = runTest {
        val adapter = FakeProviderAdapter()
        val breaker = ProviderCircuitBreaker(failureThreshold = 1)
        breaker.recordFailure(ProviderErrorKind.NETWORK)
        val useCase = TestProviderConnection(registry = registryWith(adapter, breaker = breaker))

        val result = useCase.execute()

        assertFalse(result.success)
        assertEquals(ProviderErrorKind.CIRCUIT_OPEN, result.errorKind)
        assertEquals(0, adapter.chatCalls)
    }

    @Test
    fun `a non positive timeout is rejected`() = runTest {
        val useCase = TestProviderConnection(registry = registryWith(FakeProviderAdapter()))

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase.execute(timeoutMillis = 0L) }
        }
    }

    @Test
    fun `constructing the API center and registry performs no provider call`() = runTest {
        val adapter = FakeProviderAdapter()
        val registry = registryWith(adapter)
        val useCase = TestProviderConnection(registry = registry)

        // Nothing here may contact the provider: opening the app or a screen must not consume quota.
        registry.statuses()
        useCase.toString()

        assertEquals("no probe may run implicitly", 0, adapter.chatCalls)
        assertEquals(0, adapter.listModelsCalls)
        assertEquals(0, adapter.streamCalls)
    }

    @Test
    fun `cancelling the caller cancels the probe instead of producing a verdict`() = runTest {
        val adapter = FakeProviderAdapter(delayMillis = 60_000L)
        val useCase = TestProviderConnection(registry = registryWith(adapter))

        var verdictProduced = false
        val job = launch {
            useCase.execute()
            verdictProduced = true
        }

        delay(10L)
        job.cancel()

        assertTrue(job.isCancelled)
        assertFalse("a cancelled probe must not yield a connection verdict", verdictProduced)
    }

    private fun registryWith(
        adapter: FakeProviderAdapter,
        configured: Boolean = true,
        enabled: Boolean = true,
        configuredModel: String? = "model-a",
        breaker: ProviderCircuitBreaker = ProviderCircuitBreaker(),
    ): ProviderRegistry = ProviderRegistry().register(
        ProviderEntry(
            id = ProviderId.HCNSEC,
            priority = 0,
            enabled = enabled,
            adapter = adapter,
            capabilities = adapter.capabilities,
            health = ProviderHealthTracker(),
            circuitBreaker = breaker,
            isConfigured = { configured },
            configuredModel = { configuredModel },
        ),
    )
}
