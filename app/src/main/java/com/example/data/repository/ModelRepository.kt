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

    /** Combined flow of active-provider models and user-saved custom models. */
    val allModels: Flow<List<AiModel>> = combine(
        _apiModels,
        customModelDao.getAllCustomModels()
    ) { apiList, customList ->
        val customAiModels = customList.map { entity ->
            AiModel(
                id = entity.id,
                displayName = entity.displayName,
                isCustom = true,
                isFavorite = entity.isFavorite,
                capabilities = CapabilityRegistry.detectCapabilities(entity.id),
                description = "Custom configured model for the active provider"
            )
        }

        // Merge: avoid duplicates
        val customIds = customAiModels.map { it.id }.toSet()
        val filteredApiList = apiList.filterNot { it.id in customIds }
        customAiModels + filteredApiList
    }

    suspend fun refreshModels(): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val provider = apiKeyRepository.getActiveProvider()
        if (!apiKeyRepository.hasProviderKey(provider)) {
            return@withContext Result.failure(Exception("No ${provider.displayName} API key configured"))
        }

        _isLoading.value = true
        _errorMessage.value = null

        val client = providerRegistry.get(provider.name)
            ?: return@withContext Result.failure(Exception("${provider.displayName} provider is not registered"))
        val result = client.listModels()

        if (!client.descriptor.supportsModelListing) {
            // Fallback: treat configured model id as the visible option.
            val configuredModel = apiKeyRepository.getProviderBaseUrl(provider)?.let { "${provider.name} configured model" } ?: provider.displayName
            _apiModels.value = listOf(
                AiModel(
                    id = configuredModel,
                    displayName = configuredModel,
                    isCustom = false,
                    isFavorite = false,
                    capabilities = CapabilityRegistry.detectCapabilities(configuredModel),
                    description = "${provider.displayName} model (manual endpoint)"
                )
            )
            return@withContext Result.success(_apiModels.value)
        }
        _isLoading.value = false

        result.fold(
            onSuccess = { dtoList ->
                val models = dtoList.map { model ->
                    AiModel(
                        id = model.id,
                        displayName = model.displayName,
                        isCustom = false,
                        isFavorite = false,
                        capabilities = model.capabilities.ifEmpty { CapabilityRegistry.detectCapabilities(model.id) },
                        description = model.description.ifBlank { "${provider.displayName} model" }
                    )
                }.sortedBy { it.displayName }

                _apiModels.value = models
                Result.success(models)
            },
            onFailure = { err ->
                _errorMessage.value = err.message
                Result.failure(err)
            }
        )
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
            // If it's an API model, we can save it as custom entity with isFavorite = true
            customModelDao.insert(
                CustomModelEntity(
                    id = model.id,
                    displayName = model.displayName,
                    isFavorite = !model.isFavorite
                )
            )
        }
    }
}
