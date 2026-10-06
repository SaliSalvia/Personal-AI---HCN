package com.example.data.repository

import com.example.data.api.AiProvider
import com.example.domain.provider.AiProviderRegistry
import com.example.data.local.dao.CustomModelDao
import com.example.data.local.entity.CustomModelEntity
import com.example.data.security.ApiKeyRepository
import com.example.domain.model.AiModel
import com.example.domain.router.CapabilityRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Aggregates the model catalogues of *all* configured providers. The app is no longer
 * tied to a single active provider: every provider with a stored key contributes its
 * models, and each model remembers which provider owns it ([AiModel.providerId]).
 */
class ModelRepository(
    private val providerRegistry: AiProviderRegistry,
    private val customModelDao: CustomModelDao,
    private val apiKeyRepository: ApiKeyRepository
) {
    private val _apiModels = MutableStateFlow<List<AiModel>>(emptyList())
    val apiModels = _apiModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /** Bumped whenever a favorite is toggled so [allModels] re-emits. */
    private val _favoritesVersion = MutableStateFlow(0)

    /** Combined flow of every configured provider's models plus user-saved custom models. */
    val allModels: Flow<List<AiModel>> = combine(
        _apiModels,
        customModelDao.getAllCustomModels(),
        _favoritesVersion
    ) { apiList, customList, _ ->
        val favorites = apiKeyRepository.getFavoriteModelKeys()

        val customAiModels = customList.map { entity ->
            AiModel(
                id = entity.id,
                displayName = entity.displayName,
                isCustom = true,
                isFavorite = entity.isFavorite,
                capabilities = CapabilityRegistry.detectCapabilities(entity.id),
                description = "Custom model id",
                providerId = AiProvider.CUSTOM.name
            )
        }

        // Merge: avoid duplicating a model that a provider already lists.
        val customIds = customAiModels.map { it.id }.toSet()
        val filteredApiList = apiList
            .filterNot { it.id in customIds }
            .map { model -> model.copy(isFavorite = model.isFavorite || favorites.contains(model.selectionKey)) }

        customAiModels + filteredApiList
    }

    /**
     * Loads models from every provider that has credentials. Providers are queried
     * independently, so one failing API never hides the models of the others.
     */
    suspend fun refreshModels(): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val configured = apiKeyRepository.getConfiguredProviders()
        if (configured.isEmpty()) {
            _isLoading.value = false
            _apiModels.value = emptyList()
            _errorMessage.value = "No provider is configured yet. Add an API key in Settings to load models."
            return@withContext Result.failure(Exception(_errorMessage.value))
        }

        _isLoading.value = true
        _errorMessage.value = null

        val collected = mutableListOf<AiModel>()
        val failures = mutableListOf<String>()

        for (provider in configured) {
            val client = providerRegistry.get(provider.name)
            if (client == null) {
                failures.add("${provider.displayName}: provider is not registered")
                continue
            }

            if (!client.descriptor.supportsModelListing) {
                // Endpoints that expose no catalogue still expose the manually configured id.
                val manualId = apiKeyRepository.getCustomModel().trim()
                if (provider == AiProvider.CUSTOM && manualId.isNotEmpty()) {
                    collected.add(
                        AiModel(
                            id = manualId,
                            displayName = manualId,
                            isCustom = false,
                            capabilities = CapabilityRegistry.detectCapabilities(manualId),
                            description = "${provider.displayName} model (manual endpoint)",
                            providerId = provider.name
                        )
                    )
                }
                continue
            }

            client.listModels().fold(
                onSuccess = { providerModels ->
                    collected.addAll(
                        providerModels.map { model ->
                            AiModel(
                                id = model.id,
                                displayName = model.displayName.ifBlank { model.id },
                                isCustom = false,
                                capabilities = model.capabilities.ifEmpty { CapabilityRegistry.detectCapabilities(model.id) },
                                description = model.description.ifBlank { "${provider.displayName} model" },
                                providerId = provider.name
                            )
                        }
                    )
                },
                onFailure = { err ->
                    failures.add("${provider.displayName}: ${err.message ?: "failed to load models"}")
                }
            )
        }

        val aggregated = collected
            .distinctBy { it.selectionKey }
            .sortedWith(compareBy({ AiProvider.displayNameOf(it.providerId) }, { it.displayName.lowercase() }))

        _apiModels.value = aggregated
        _isLoading.value = false

        if (aggregated.isEmpty()) {
            val message = failures.joinToString("; ").ifBlank { "No models were returned by the configured providers." }
            _errorMessage.value = message
            Result.failure(Exception(message))
        } else {
            if (failures.isNotEmpty()) _errorMessage.value = failures.joinToString("; ")
            Result.success(aggregated)
        }
    }

    suspend fun addCustomModel(modelId: String, displayName: String = modelId): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedId = modelId.trim()
        if (trimmedId.isEmpty()) return@withContext Result.failure(Exception("Model ID cannot be empty"))

        customModelDao.insert(
            CustomModelEntity(
                id = trimmedId,
                displayName = displayName.ifBlank { trimmedId }
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteCustomModel(modelId: String) = withContext(Dispatchers.IO) {
        customModelDao.deleteById(modelId)
    }

    /**
     * Toggles a favorite. Favorites of provider models are persisted separately from
     * the custom-model table, so favoriting a listed model never spawns a duplicate
     * "custom" row in the picker.
     */
    suspend fun toggleFavorite(model: AiModel) = withContext(Dispatchers.IO) {
        if (model.isCustom) {
            customModelDao.update(
                CustomModelEntity(
                    id = model.id,
                    displayName = model.displayName,
                    isFavorite = !model.isFavorite
                )
            )
        } else {
            apiKeyRepository.toggleFavoriteModelKey(model.selectionKey)
        }
        _favoritesVersion.value++
    }
}
