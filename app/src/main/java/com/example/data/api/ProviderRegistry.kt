package com.example.data.api

import com.example.domain.provider.AiProviderClient
import com.example.domain.provider.AiProviderRegistry
import com.example.domain.provider.ProviderDescriptor
import java.util.concurrent.ConcurrentHashMap

/** Reference implementation of [AiProviderRegistry]. Providers register themselves at
 * app startup, and the registry resolves the client used by [com.example.data.repository.ModelRepository]
 * and [com.example.data.repository.ChatRepository].*/
class DefaultAiProviderRegistry(
    override val defaultProviderId: String = AiProvider.HCNSEC.name
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

    override fun all(): List<ProviderDescriptor> =
        clients.values.map { it.descriptor }.sortedBy { it.displayName }

    /** Default client is HCNSEC-registered adapter unless another provider took the default id. */
    override fun defaultClient(): AiProviderClient? = get(defaultProviderId)
}

/** Runtime contract for provider implementations.
 *
 * New providers are added by implementing this interface and registering via
 * [AiProviderRegistry.register]. The rest of the app only calls the registry, never the concrete
 * transport class directly.*/
interface AiProviderClient {
    val descriptor: ProviderDescriptor
    suspend fun listModels(): Result<List<com.example.domain.provider.ProviderModel>>
    fun streamChat(
        modelId: String,
        messages: List<com.example.domain.provider.ProviderMessage>,
        temperature: Double
    ): kotlinx.coroutines.flow.Flow<com.example.domain.provider.ProviderStreamEvent>
}
