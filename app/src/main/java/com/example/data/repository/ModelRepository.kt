package com.example.data.repository

import com.example.data.api.hcnsec.HcnsecProviderGateway
import com.example.data.local.dao.CustomModelDao
import com.example.data.local.entity.CustomModelEntity
import com.example.domain.model.AiModel
import com.example.domain.router.CapabilityRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class ModelRepository(
    private val providerGateway: HcnsecProviderGateway,
    private val customModelDao: CustomModelDao
) {
    private val _apiModels = MutableStateFlow<List<AiModel>>(emptyList())
    val apiModels = _apiModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /**
     * Combined flow of official HCNSEC models and user-saved custom models.
     */
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
                description = "Custom configured HCNSEC Model"
            )
        }

        // Merge: avoid duplicates
        val customIds = customAiModels.map { it.id }.toSet()
        val filteredApiList = apiList.filterNot { it.id in customIds }
        customAiModels + filteredApiList
    }

    suspend fun refreshModels(): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        if (!providerGateway.isConfigured()) {
            return@withContext Result.failure(Exception("No HCNSEC API key is configured"))
        }

        _isLoading.value = true
        _errorMessage.value = null

        val result = providerGateway.listModels()
        _isLoading.value = false

        result.fold(
            onSuccess = { models ->
                _apiModels.value = models.sortedBy { it.displayName }
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
