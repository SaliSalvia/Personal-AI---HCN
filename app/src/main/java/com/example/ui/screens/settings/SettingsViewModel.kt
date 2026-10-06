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
    data object Idle : ConnectionTestState()
    data object Testing : ConnectionTestState()
    data class Success(val provider: AiProvider, val modelCount: Int) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

/**
 * Settings now manage a *set* of provider credentials. Any number of providers can be
 * connected at the same time and all of them stay live for the agent.
 */
class SettingsViewModel(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiClient: HcnsecApiClient,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _configuredProviders = MutableStateFlow(apiKeyRepository.getConfiguredProviders().toSet())
    val configuredProviders = _configuredProviders.asStateFlow()

    fun isProviderConfigured(provider: AiProvider): Boolean = provider in _configuredProviders.value

    fun providerKeyStatus(provider: AiProvider): String = apiKeyRepository.getMaskedProviderKey(provider)

    fun refreshConfigured() {
        _configuredProviders.value = apiKeyRepository.getConfiguredProviders().toSet()
    }

    /**
     * Validates and stores a key for [provider]. The provider can be auto-detected from
     * the key prefix, and the key is stored under the detected provider so a pasted key
     * is never filed under the wrong API.
     */
    fun saveProviderKey(
        provider: AiProvider,
        key: String,
        customBaseUrl: String? = null,
        customModel: String? = null,
        onSuccess: (AiProvider) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val detectedProvider = if (provider == AiProvider.CUSTOM) {
                provider
            } else {
                AiProvider.detectFromKey(key) ?: provider
            }
            try {
                if (detectedProvider == AiProvider.CUSTOM) {
                    apiKeyRepository.saveCustomProvider(customBaseUrl.orEmpty(), customModel)
                }
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Invalid custom provider settings")
                return@launch
            }
            val result = apiClient.validateApiKey(detectedProvider, key)
            result.fold(
                onSuccess = {
                    apiKeyRepository.saveProviderKey(detectedProvider, key)
                    refreshConfigured()
                    modelRepository.refreshModels()
                    onSuccess(detectedProvider)
                },
                onFailure = { onError(it.message ?: "Provider key validation failed") }
            )
        }
    }

    /** Removes one provider's key; [onAllRemoved] fires only when no provider is left. */
    fun removeProviderKey(provider: AiProvider, onAllRemoved: () -> Unit = {}) {
        viewModelScope.launch {
            if (provider == AiProvider.CUSTOM) {
                apiKeyRepository.clearCustomProvider()
            } else {
                apiKeyRepository.clearProviderKey(provider)
            }
            refreshConfigured()
            modelRepository.refreshModels()
            if (_configuredProviders.value.isEmpty()) onAllRemoved()
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

    fun clearTestState() {
        _testState.value = ConnectionTestState.Idle
    }

    fun setAutoRouting(enabled: Boolean) {
        _isAutoRouting.value = enabled
        apiKeyRepository.setAutoRoutingEnabled(enabled)
    }

    fun setDefaultModel(modelId: String) {
        _defaultModel.value = modelId
        apiKeyRepository.setDefaultModel(modelId)
    }

    /** Validates the stored key of a single provider without touching the others. */
    fun testProvider(provider: AiProvider) {
        val key = apiKeyRepository.getProviderKey(provider)
        if (key.isNullOrBlank()) {
            _testState.value = ConnectionTestState.Error("No key stored for ${provider.displayName}")
            return
        }
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            apiClient.validateApiKey(provider, key).fold(
                onSuccess = { models ->
                    _testState.value = ConnectionTestState.Success(provider, models.size)
                },
                onFailure = { err ->
                    _testState.value = ConnectionTestState.Error(err.message ?: "Connection test failed")
                }
            )
        }
    }

    fun removeAllKeys(onRemoved: () -> Unit) {
        viewModelScope.launch {
            AiProvider.catalog.forEach { provider ->
                if (provider == AiProvider.CUSTOM) {
                    apiKeyRepository.clearCustomProvider()
                } else {
                    apiKeyRepository.clearProviderKey(provider)
                }
            }
            refreshConfigured()
            modelRepository.refreshModels()
            onRemoved()
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
