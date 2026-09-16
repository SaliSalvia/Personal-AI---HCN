package com.example.domain.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic adapter fake used by registry and connection-test suites.
 *
 * Contains no credentials and performs no IO.
 */
internal class FakeProviderAdapter(
    override val id: ProviderId = ProviderId.HCNSEC,
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.CHAT_COMPLETION,
        ProviderCapability.STREAMING,
        ProviderCapability.CONNECTION_TEST,
    ),
    private val responseText: String = "ok",
    private val latencyMillis: Long = 5L,
    private val failWith: ProviderError? = null,
    private val delayMillis: Long = 0L,
    private val modelIds: List<String> = listOf("fake-model"),
) : ProviderAdapter {

    var chatCalls: Int = 0
        private set

    var streamCalls: Int = 0
        private set

    var listModelsCalls: Int = 0
        private set

    var lastRequest: ProviderChatRequest? = null
        private set

    override suspend fun listModels(): List<ProviderModelDescriptor> {
        listModelsCalls += 1
        failWith?.let { throw ProviderException(it) }
        return modelIds.map { ProviderModelDescriptor(id = it) }
    }

    override suspend fun chat(request: ProviderChatRequest): ProviderChatResponse {
        chatCalls += 1
        lastRequest = request
        if (delayMillis > 0) delay(delayMillis)
        failWith?.let { throw ProviderException(it) }
        return ProviderChatResponse(
            text = responseText,
            model = request.model ?: "fake-model",
            latencyMillis = latencyMillis,
        )
    }

    override fun stream(request: ProviderChatRequest): Flow<ProviderStreamEvent> {
        streamCalls += 1
        lastRequest = request
        return flowOf(ProviderStreamEvent.Completed(finishReason = "stop"))
    }
}
