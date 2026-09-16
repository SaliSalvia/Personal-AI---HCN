package com.example.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry behaviour: single source of truth, no duplicate definitions, correct gating and
 * side-effect-free queries.
 */
class ProviderRegistryTest {

    private lateinit var adapter: FakeProviderAdapter
    private lateinit var health: ProviderHealthTracker
    private lateinit var breaker: ProviderCircuitBreaker

    private fun registry(
        configured: Boolean = true,
        enabled: Boolean = true,
        models: List<String> = listOf("model-a", "model-b"),
        configuredModel: String? = "model-a",
    ): ProviderRegistry {
        adapter = FakeProviderAdapter(capabilities = setOf(ProviderCapability.CHAT_COMPLETION, ProviderCapability.STREAMING))
        health = ProviderHealthTracker()
        breaker = ProviderCircuitBreaker()
        return ProviderRegistry().register(
            ProviderEntry(
                id = ProviderId.HCNSEC,
                priority = 0,
                enabled = enabled,
                adapter = adapter,
                capabilities = adapter.capabilities,
                health = health,
                circuitBreaker = breaker,
                isConfigured = { configured },
                configuredModel = { configuredModel },
            ),
        ).also { it.updateKnownModels(ProviderId.HCNSEC, models.map { id -> ProviderModelDescriptor(id = id) }) }
    }

    @Test
    fun `registered provider is retrievable with its metadata`() {
        val registry = registry()

        val entry = registry.find(ProviderId.HCNSEC)
        assertEquals(ProviderId.HCNSEC, entry?.id)
        assertEquals(0, entry?.priority)
        assertEquals(setOf(ProviderCapability.CHAT_COMPLETION, ProviderCapability.STREAMING), entry?.capabilities)

        val status = registry.status(ProviderId.HCNSEC)
        assertEquals(true, status?.configured)
        assertEquals(true, status?.enabled)
        assertEquals(true, status?.hasAdapter)
        assertEquals("model-a", status?.configuredModel)
        assertEquals(listOf("model-a", "model-b"), status?.models)
        assertEquals(ProviderHealthState.UNKNOWN, status?.health)
        assertEquals(CircuitState.CLOSED, status?.circuitState)
        assertTrue(status?.isReady == true)
    }

    @Test
    fun `duplicate provider definitions are rejected`() {
        val registry = registry()

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(
                ProviderEntry(
                    id = ProviderId.HCNSEC,
                    priority = 5,
                    enabled = true,
                    adapter = adapter,
                    capabilities = adapter.capabilities,
                    health = health,
                    circuitBreaker = breaker,
                ),
            )
        }
    }

    @Test
    fun `unregistered provider fails with a normalized error`() {
        val registry = registry()

        val failure = assertThrows(ProviderException::class.java) { registry.adapter(ProviderId.GROQ) }

        assertEquals(ProviderErrorKind.UNKNOWN, failure.kind)
    }

    @Test
    fun `reserved later phase slots stay disabled and adapterless`() {
        val registry = registry()
            .registerReserved(ProviderId.GEMINI, priority = 10)
            .registerReserved(ProviderId.GROQ, priority = 20)

        val gemini = registry.status(ProviderId.GEMINI)
        assertNull(gemini?.configuredModel)
        assertEquals(false, gemini?.enabled)
        assertEquals(false, gemini?.configured)
        assertEquals(false, gemini?.hasAdapter)
        assertEquals(ProviderHealthState.DISABLED, gemini?.health)
        assertFalse(gemini?.isReady == true)

        val failure = assertThrows(ProviderException::class.java) { registry.adapter(ProviderId.GEMINI) }
        assertEquals(ProviderErrorKind.PROVIDER_DISABLED, failure.kind)
    }

    @Test
    fun `providers are ordered by priority`() {
        val registry = registry().registerReserved(ProviderId.GEMINI, priority = 10)

        assertEquals(listOf(ProviderId.HCNSEC, ProviderId.GEMINI), registry.all().map { it.id })
        assertEquals(listOf(ProviderId.HCNSEC, ProviderId.GEMINI), registry.statuses().map { it.id })
    }

    @Test
    fun `adapter is gated on configuration`() {
        val registry = registry(configured = false)

        val failure = assertThrows(ProviderException::class.java) { registry.adapter(ProviderId.HCNSEC) }

        assertEquals(ProviderErrorKind.NOT_CONFIGURED, failure.kind)
        assertEquals(0, adapter.chatCalls)
    }

    @Test
    fun `adapter is gated on the circuit breaker`() {
        val registry = registry()
        registry.find(ProviderId.HCNSEC)?.circuitBreaker?.let { openBreaker(it) }

        val failure = assertThrows(ProviderException::class.java) { registry.adapter(ProviderId.HCNSEC) }

        assertEquals(ProviderErrorKind.CIRCUIT_OPEN, failure.kind)
    }

    @Test
    fun `disabling a provider blocks the adapter and updates health`() {
        val registry = registry()

        registry.setEnabled(ProviderId.HCNSEC, enabled = false)

        assertEquals(ProviderHealthState.DISABLED, registry.status(ProviderId.HCNSEC)?.health)
        val failure = assertThrows(ProviderException::class.java) { registry.adapter(ProviderId.HCNSEC) }
        assertEquals(ProviderErrorKind.PROVIDER_DISABLED, failure.kind)
    }

    @Test
    fun `candidates expose routing metadata and honour gating`() {
        val registry = registry().registerReserved(ProviderId.GEMINI, priority = 10)

        val candidates = registry.candidates(ProviderCapability.STREAMING)
        assertEquals(listOf(ProviderId.HCNSEC), candidates.map { it.id })

        // Reserved slots have no capabilities and are never candidates.
        assertTrue(registry.candidates(ProviderCapability.CONNECTION_TEST).isEmpty())
    }

    @Test
    fun `queries do not consume circuit probes`() {
        var now = 0L
        val registry = registry()
        val breaker = ProviderCircuitBreaker(failureThreshold = 1, openDurationMillis = 100L, clock = { now })
        val gated = registry.register(
            ProviderEntry(
                id = ProviderId.MISTRAL,
                priority = 5,
                enabled = true,
                adapter = FakeProviderAdapter(id = ProviderId.MISTRAL),
                capabilities = setOf(ProviderCapability.CHAT_COMPLETION),
                health = ProviderHealthTracker(),
                circuitBreaker = breaker,
                isConfigured = { true },
            ),
        )
        openBreaker(gated.find(ProviderId.MISTRAL)!!.circuitBreaker)

        now = 200L
        repeat(3) { gated.candidates(ProviderCapability.CHAT_COMPLETION) }

        assertEquals("queries must not admit a probe", CircuitState.HALF_OPEN, breaker.currentState())
        assertTrue("the real probe is still available", breaker.allowRequest())
    }

    @Test
    fun `an authenticating provider is not a candidate`() {
        val registry = registry()
        health.recordFailure(ProviderErrors.authentication("rejected"))

        assertTrue(registry.candidates(ProviderCapability.CHAT_COMPLETION).isEmpty())
        assertFalse(registry.status(ProviderId.HCNSEC)?.isReady == true)
    }

    private fun openBreaker(breaker: ProviderCircuitBreaker) {
        repeat(3) { breaker.recordFailure(ProviderErrorKind.NETWORK) }
    }
}
