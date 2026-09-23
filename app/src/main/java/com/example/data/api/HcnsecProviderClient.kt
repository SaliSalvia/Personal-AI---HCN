package com.example.data.api

import com.example.domain.model.ModelCapability
import com.example.domain.provider.AiProviderClient
import com.example.domain.provider.ProviderDescriptor
import com.example.domain.provider.ProviderMessage
import com.example.domain.provider.ProviderModel
import com.example.domain.provider.ProviderStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Compatibility adapter around the existing HTTP transport. New providers can
 * implement [AiProviderClient] directly without changing ChatRepository.
 */
class HcnsecProviderClient(
    private val transport: HcnsecApiClient,
    private val provider: AiProvider
) : AiProviderClient {
    override val descriptor = ProviderDescriptor(
        id = provider.name,
        displayName = provider.displayName,
        description = provider.description,
        supportsModelListing = provider != AiProvider.CUSTOM,
        supportsStreaming = true
    )

    override suspend fun listModels(): Result<List<ProviderModel>> =
        transport.getModels().map { models -> models.map { it.toProviderModel() } }

    override fun streamChat(
        modelId: String,
        messages: List<ProviderMessage>,
        temperature: Double
    ): Flow<ProviderStreamEvent> = transport.streamChatCompletion(
        model = modelId,
        messages = messages.map { ChatMessageDto(role = it.role, content = it.content) },
        temperature = temperature
    ).map { event ->
        when (event) {
            is StreamEvent.Content -> ProviderStreamEvent.Content(event.text)
            is StreamEvent.Reasoning -> ProviderStreamEvent.Reasoning(event.reasoningText)
            is StreamEvent.Completed -> ProviderStreamEvent.Completed(event.finishReason, event.totalTokens)
            is StreamEvent.Error -> ProviderStreamEvent.Error(event.message, event.code)
        }
    }

    private fun HcnsecModelDto.toProviderModel(): ProviderModel {
        val capabilities = com.example.domain.router.CapabilityRegistry.detectCapabilities(id)
        return ProviderModel(
            id = id,
            displayName = id,
            capabilities = capabilities,
            description = "${provider.displayName} model (${ownedBy ?: provider.displayName})"
        )
    }
}
