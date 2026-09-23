package com.example.domain.provider

import com.example.domain.model.ModelCapability
import kotlinx.coroutines.flow.Flow

/** Stable identity and UI metadata for an AI provider implementation. */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    val supportsModelListing: Boolean = true,
    val supportsStreaming: Boolean = true
) {
    /** Whether this provider needs a separate configuration entry before use. */
    val requiresConfiguration: Boolean get() = description.contains("configuration", ignoreCase = true)
}

data class ProviderModel(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<ModelCapability> = emptySet(),
    val description: String = ""
)

data class ProviderMessage(
    val role: String,
    val content: String
)

sealed class ProviderStreamEvent {
    data class Content(val text: String) : ProviderStreamEvent()
    data class Reasoning(val text: String, val reasoningText: String = text) : ProviderStreamEvent()
    data class Completed(val finishReason: String? = null, val totalTokens: Int? = null) : ProviderStreamEvent()
    data class Error(val message: String, val code: Int? = null) : ProviderStreamEvent()
}

/** One provider implementation. It owns transport/protocol details, not UI state. */
interface AiProviderClient {
    val descriptor: ProviderDescriptor

    suspend fun listModels(): Result<List<ProviderModel>>

    fun streamChat(
        modelId: String,
        messages: List<ProviderMessage>,
        temperature: Double = 0.7
    ): Flow<ProviderStreamEvent>
}

/** Runtime registry that allows built-in and user-supplied providers to coexist. */
interface AiProviderRegistry {
    val defaultProviderId: String
    fun register(client: AiProviderClient)
    fun unregister(providerId: String)
    fun get(providerId: String): AiProviderClient?
    fun all(): List<ProviderDescriptor>
    fun defaultClient(): AiProviderClient?
}
