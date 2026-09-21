package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.AiProvider
import com.example.data.api.HcnsecApiClient
import com.example.data.api.UserBalanceDto
import com.example.data.repository.ModelRepository
import com.example.data.security.ApiKeyRepository
import com.example.domain.model.AiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val modelCount: Int) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

class SettingsViewModel(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiClient: HcnsecApiClient,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val maskedApiKey: String
        get() = apiKeyRepository.getMaskedApiKey()

    private val _activeProvider = MutableStateFlow(apiKeyRepository.getActiveProvider())
    val activeProvider = _activeProvider.asStateFlow()

    fun selectProvider(provider: AiProvider) {
        apiKeyRepository.setActiveProvider(provider)
        _activeProvider.value = provider
    }

    fun providerKeyStatus(provider: AiProvider): String = apiKeyRepository.getMaskedProviderKey(provider)

    fun saveProviderKey(provider: AiProvider, key: String, onSuccess: (AiProvider) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val detectedProvider = AiProvider.detectFromKey(key) ?: provider
            apiKeyRepository.setActiveProvider(detectedProvider)
            _activeProvider.value = detectedProvider
            val result = apiClient.validateApiKey(key)
            result.fold(
                onSuccess = {
                    apiKeyRepository.saveProviderKey(detectedProvider, key)
                    modelRepository.refreshModels()
                    onSuccess(detectedProvider)
                },
                onFailure = { onError(it.message ?: "Provider key validation failed") }
            )
        }
    }

    private val _isAutoRouting = MutableStateFlow(apiKeyRepository.isAutoRoutingEnabled())
    val isAutoRouting = _isAutoRouting.asStateFlow()

    private val _defaultModel = MutableStateFlow(apiKeyRepository.getDefaultModel())
    val defaultModel = _defaultModel.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState = _testState.asStateFlow()

    private val _accountUsage = MutableStateFlow<UserBalanceDto?>(null)
    val accountUsage = _accountUsage.asStateFlow()

    val availableModels: StateFlow<List<AiModel>> =
        modelRepository.allModels
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadAccountUsage()
    }

    fun setAutoRouting(enabled: Boolean) {
        _isAutoRouting.value = enabled
        apiKeyRepository.setAutoRoutingEnabled(enabled)
    }

    fun setDefaultModel(modelId: String) {
        _defaultModel.value = modelId
        apiKeyRepository.setDefaultModel(modelId)
    }

    fun testConnection() {
        apiKeyRepository.setActiveProvider(AiProvider.HCNSEC)
        _activeProvider.value = AiProvider.HCNSEC
        val key = apiKeyRepository.getApiKey()
        if (key.isNullOrBlank()) {
            _testState.value = ConnectionTestState.Error("No API key stored")
            return
        }

        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            val result = apiClient.validateApiKey(key)
            result.fold(
                onSuccess = { models ->
                    _testState.value = ConnectionTestState.Success(models.size)
                },
                onFailure = { err ->
                    _testState.value = ConnectionTestState.Error(err.message ?: "Connection test failed")
                }
            )
        }
    }

    fun updateApiKey(newKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        apiKeyRepository.setActiveProvider(AiProvider.HCNSEC)
        _activeProvider.value = AiProvider.HCNSEC
        viewModelScope.launch {
            val result = apiClient.validateApiKey(newKey)
            result.fold(
                onSuccess = {
                    apiKeyRepository.saveApiKey(newKey)
                    modelRepository.refreshModels()
                    onSuccess()
                },
                onFailure = { err ->
                    onError(err.message ?: "Failed to validate new key")
                }
            )
        }
    }

    fun removeApiKey(onRemoved: () -> Unit) {
        apiKeyRepository.clearApiKey()
        onRemoved()
    }

    fun addCustomModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.addCustomModel(modelId)
        }
    }

    fun deleteCustomModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteCustomModel(modelId)
        }
    }

    private fun loadAccountUsage() {
        viewModelScope.launch {
            val result = apiClient.getAccountUsage()
            _accountUsage.value = result.getOrNull()
        }
    }

    class Factory(
        private val apiKeyRepository: ApiKeyRepository,
        private val apiClient: HcnsecApiClient,
        private val modelRepository: ModelRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(apiKeyRepository, apiClient, modelRepository) as T
        }
    }
}
