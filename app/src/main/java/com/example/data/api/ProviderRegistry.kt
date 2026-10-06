package com.example.data.api

import com.example.domain.provider.AiProviderClient
import com.example.domain.provider.AiProviderRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Reference implementation of [AiProviderRegistry]. Every configured provider registers
 * itself at app startup, so several providers can be live at the same time. The registry
 * no longer privileges a single "default" provider: [defaultProviderId] is optional and
 * [defaultClient] falls back to the first registered provider when it is unset.
 */
class DefaultAiProviderRegistry(
    override val defaultProviderId: String = ""
) : AiProviderRegistry {
    private val clients = ConcurrentHashMap<String, AiProviderClient>()

    override fun register(client: AiProviderClient) {
        require(client.descriptor.id.isNotBlank()) { "Provider id cannot be blank" }
        clients[client.descriptor.id] = client
    }

    override fun unregister(providerId: String) {
        clients.remove(providerId)
    }

    override fun get(providerId: String): AiProviderClient? = clients[providerId]

    override fun all(): List<com.example.domain.provider.ProviderDescriptor> =
        clients.values.map { it.descriptor }.sortedBy { it.displayName }

    /** The configured default client when set, otherwise the first provider by display name. */
    override fun defaultClient(): AiProviderClient? =
        get(defaultProviderId) ?: clients.values.minByOrNull { it.descriptor.displayName }
}
