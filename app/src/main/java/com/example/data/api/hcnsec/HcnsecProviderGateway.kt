package com.example.data.api.hcnsec

import com.example.data.security.ProviderCredential
import com.example.domain.model.AiModel
import com.example.domain.provider.ProviderAdapter
import com.example.domain.provider.ProviderChatRequest
import com.example.domain.provider.ProviderErrors
import com.example.domain.provider.ProviderException
import com.example.domain.provider.ProviderId
import com.example.domain.provider.ProviderModelDescriptor
import com.example.domain.provider.ProviderRegistry
import com.example.domain.provider.ProviderStatus
import com.example.domain.provider.ProviderStreamEvent
import com.example.domain.provider.ProviderTestConnectionResult
import com.example.domain.provider.TestProviderConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Application-facing entry point for the HCNSEC provider.
 *
 * This class holds no provider logic of its own: every operation is delegated to the provider
 * registry and the registered HCNSEC adapter, so the registry remains the single source of truth
 * for provider definitions (Phase 4.2 §12). It exists only to keep repositories and view models
 * free of provider-specific types (no wire models, no HTTP, no credentials).
 *
 * Request path implemented here:
 * `application -> registry -> HCNSEC adapter -> HTTPS transport -> normalized response`.
 */
class HcnsecProviderGateway(
    private val registry: ProviderRegistry,
    private val hcnsecAdapter: HcnsecProviderAdapter,
    private val connectionTester: TestProviderConnection,
) {

    /**
     * Streams a chat completion.
     *
     * @throws ProviderException when the provider is disabled, unconfigured or its circuit is open.
     *   Failures that occur after the stream starts are delivered as
     *   [ProviderStreamEvent.Failed]; cancellation propagates as [CancellationException].
     */
    fun streamChat(request: ProviderChatRequest): Flow<ProviderStreamEvent> =
        resolvedAdapter().stream(request)

    /** Lists the models the configured account can use, keeping the registry's metadata fresh. */
    suspend fun listModels(): Result<List<AiModel>> = try {
        val descriptors = hcnsecAdapter.listModels()
        registry.updateKnownModels(ProviderId.HCNSEC, descriptors)
        Result.success(descriptors.map { it.toAiModel() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Validates a credential the user has typed but not yet stored (first-run onboarding and the
     * "update key" dialog).
     *
     * The value is kept only in memory for the duration of the call: it is never persisted here,
     * never logged and never placed in an error. On success the caller stores it through the
     * hardware-backed credential repository.
     */
    suspend fun verifyEphemeralCredential(ephemeralKey: String): Result<List<AiModel>> {
        val trimmed = ephemeralKey.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                ProviderException(ProviderErrors.notConfigured(ProviderId.HCNSEC, "the credential is empty.")),
            )
        }

        return try {
            val descriptors = hcnsecAdapter.validateEphemeralCredential(ProviderCredential(trimmed))
            registry.updateKnownModels(ProviderId.HCNSEC, descriptors)
            Result.success(descriptors.map { it.toAiModel() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs a user-triggered connection test. Never called automatically.
     */
    suspend fun testConnection(
        model: String?,
        timeoutMillis: Long = TestProviderConnection.DEFAULT_TIMEOUT_MILLIS,
    ): ProviderTestConnectionResult = connectionTester.execute(
        provider = ProviderId.HCNSEC,
        model = model,
        timeoutMillis = timeoutMillis,
    )

    /** Safe, secret-free provider status for the API Center. */
    fun status(): ProviderStatus? = registry.status(ProviderId.HCNSEC)

    /** True when a stored credential exists. Never reveals the credential. */
    fun isConfigured(): Boolean = registry.find(ProviderId.HCNSEC)?.isConfigured() == true

    private fun resolvedAdapter(): ProviderAdapter = registry.adapter(ProviderId.HCNSEC)

    private fun ProviderModelDescriptor.toAiModel(): AiModel = AiModel(
        id = id,
        displayName = displayName,
        isCustom = false,
        isFavorite = false,
        capabilities = capabilities,
        description = description,
    )
}
