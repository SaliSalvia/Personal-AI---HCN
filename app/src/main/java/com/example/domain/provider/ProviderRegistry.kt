package com.example.domain.provider

/**
 * One registered provider slot.
 *
 * This is the single definition of a provider's identity, priority, enabled/configured state,
 * capability metadata, health and circuit breaker. Nothing else in the app may define providers.
 *
 * @param adapter the real adapter, or null for reserved foundation-only slots.
 * @param isConfigured dynamic check (for example "a credential is stored"). Never returns or
 *   exposes the credential itself.
 * @param configuredModel dynamic accessor for the user-selected model identifier.
 */
data class ProviderEntry(
    val id: ProviderId,
    val priority: Int,
    val enabled: Boolean,
    val adapter: ProviderAdapter?,
    val capabilities: Set<ProviderCapability>,
    val health: ProviderHealthTracker,
    val circuitBreaker: ProviderCircuitBreaker,
    val isConfigured: () -> Boolean = { false },
    val configuredModel: () -> String? = { null },
) {
    val displayName: String get() = id.displayName
}

/**
 * Provider status as shown by the API Center.
 *
 * Contains only safe, non-secret information: no credential, no header, no raw provider payload.
 */
data class ProviderStatus(
    val id: ProviderId,
    val displayName: String,
    val enabled: Boolean,
    val configured: Boolean,
    val hasAdapter: Boolean,
    val health: ProviderHealthState,
    val circuitState: CircuitState,
    val capabilities: Set<ProviderCapability>,
    val priority: Int,
    val configuredModel: String?,
    val models: List<String>,
    val quota: ProviderQuotaSnapshot,
    val rateLimit: ProviderRateLimitSnapshot,
    val lastLatencyMillis: Long?,
    val lastErrorMessage: String?,
) {
    /** True only when this provider can actually serve a request right now. */
    val isReady: Boolean
        get() = enabled && configured && hasAdapter && circuitState != CircuitState.OPEN &&
            health != ProviderHealthState.AUTH_ERROR &&
            health != ProviderHealthState.DISABLED
}

/**
 * The provider registry: single source of truth for provider definitions.
 *
 * The registry is deliberately side-effect free when queried — no health mutation, no circuit
 * probe admission, no network access.
 */
class ProviderRegistry {

    private val lock = Any()
    private val entries = LinkedHashMap<ProviderId, ProviderEntry>()
    private val knownModels = mutableMapOf<ProviderId, List<ProviderModelDescriptor>>()

    /** Registers a provider slot. Registering the same id twice is a programming error. */
    fun register(entry: ProviderEntry): ProviderRegistry {
        synchronized(lock) {
            require(!entries.containsKey(entry.id)) {
                "Provider ${entry.id} is already registered; the registry must not duplicate definitions."
            }
            entries[entry.id] = entry
        }
        return this
    }

    /**
     * Registers a reserved, disabled slot with no adapter.
     *
     * Used for later-phase providers so their identity, priority and enabled state are preserved
     * without implementing them (Phase 4.2 §12: other providers remain foundation-only).
     */
    fun registerReserved(id: ProviderId, priority: Int): ProviderRegistry = register(
        ProviderEntry(
            id = id,
            priority = priority,
            enabled = false,
            adapter = null,
            capabilities = emptySet(),
            health = ProviderHealthTracker().also { it.setEnabled(false) },
            circuitBreaker = ProviderCircuitBreaker(),
            isConfigured = { false },
            configuredModel = { null },
        ),
    )

    /** All registered entries ordered by priority, then identifier. */
    fun all(): List<ProviderEntry> = synchronized(lock) {
        entries.values.sortedWith(compareBy({ it.priority }, { it.id.ordinal }))
    }

    fun find(id: ProviderId): ProviderEntry? = synchronized(lock) { entries[id] }

    /**
     * Returns the real adapter for [id].
     *
     * @throws ProviderException with a normalized error when the provider has no usable adapter.
     */
    fun adapter(id: ProviderId): ProviderAdapter {
        val entry = find(id) ?: throw ProviderException(
            ProviderErrors.unknown("Provider $id is not registered."),
        )

        if (!entry.enabled) throw ProviderException(ProviderErrors.disabled(id))

        val adapter = entry.adapter ?: throw ProviderException(
            ProviderErrors.disabled(id),
        )

        if (!entry.isConfigured()) throw ProviderException(
            ProviderErrors.notConfigured(id, "no credential is configured."),
        )

        if (entry.circuitBreaker.currentState() == CircuitState.OPEN) {
            throw ProviderException(ProviderErrors.circuitOpen(id))
        }

        return adapter
    }

    /** Marks a provider enabled/disabled and keeps its health state consistent. */
    fun setEnabled(id: ProviderId, enabled: Boolean) {
        synchronized(lock) {
            val current = entries[id] ?: return
            entries[id] = current.copy(enabled = enabled)
            current.health.setEnabled(enabled)
        }
    }

    /** Records the last known model catalogue for a provider (registry model metadata). */
    fun updateKnownModels(id: ProviderId, models: List<ProviderModelDescriptor>) {
        synchronized(lock) {
            knownModels[id] = models
        }
    }

    /**
     * Providers that could serve a request with [capability], best priority first.
     *
     * Routing metadata only: this performs no network access and does not admit circuit probes,
     * so it is safe to call from the UI thread.
     */
    fun candidates(capability: ProviderCapability): List<ProviderEntry> = all().filter { entry ->
        entry.enabled &&
            entry.adapter != null &&
            entry.capabilities.contains(capability) &&
            entry.isConfigured() &&
            entry.circuitBreaker.currentState() != CircuitState.OPEN &&
            entry.health.snapshot.value.state != ProviderHealthState.AUTH_ERROR
    }

    /** Status snapshots for the API Center. Contains no secret material. */
    fun statuses(): List<ProviderStatus> = all().map { entry ->
        val health = entry.health.snapshot.value
        val circuit = entry.circuitBreaker.snapshot()
        ProviderStatus(
            id = entry.id,
            displayName = entry.displayName,
            enabled = entry.enabled,
            configured = entry.isConfigured(),
            hasAdapter = entry.adapter != null,
            health = health.state,
            circuitState = circuit.state,
            capabilities = entry.capabilities,
            priority = entry.priority,
            configuredModel = entry.configuredModel(),
            models = synchronized(lock) { knownModels[entry.id]?.map { it.id }.orEmpty() },
            quota = health.quota,
            rateLimit = health.rateLimit,
            lastLatencyMillis = health.lastLatencyMillis,
            lastErrorMessage = health.lastErrorMessage,
        )
    }

    fun status(id: ProviderId): ProviderStatus? = statuses().firstOrNull { it.id == id }
}
