package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.hcnsec.HcnsecProviderConfig
import com.example.data.api.hcnsec.HcnsecProviderGateway
import com.example.data.repository.ModelRepository
import com.example.data.security.ApiKeyRepository
import com.example.domain.model.AiModel
import com.example.domain.provider.ProviderErrorKind
import com.example.domain.provider.ProviderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data object Testing : ConnectionTestState()

    /** Latency and resolved model are the useful, non-secret parts of a successful probe. */
    data class Success(val latencyMillis: Long?, val model: String?) : ConnectionTestState()

    /** Normalized failure category plus a secret-free message. */
    data class Error(val message: String, val errorKind: ProviderErrorKind? = null) : ConnectionTestState()
}

/**
 * API Center state.
 *
 * Important: nothing in this view model contacts the network on construction or on screen open.
 * The only network work is [testConnection] (explicit user action) and [updateApiKey]
 * (explicit user action).
 */
class SettingsViewModel(
    private val apiKeyRepository: ApiKeyRepository,
    private val providerGateway: HcnsecProviderGateway,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val maskedApiKey: String
        get() = apiKeyRepository.getMaskedApiKey()

    private val _isAutoRouting = MutableStateFlow(apiKeyRepository.isAutoRoutingEnabled())
    val isAutoRouting = _isAutoRouting.asStateFlow()

    private val _defaultModel = MutableStateFlow(apiKeyRepository.getDefaultModel())
    val defaultModel = _defaultModel.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState = _testState.asStateFlow()

    /**
     * Safe provider status for the API Center: configured/enabled flags, health, circuit state,
     * configured model and whatever rate-limit/quota information the provider actually reported.
     * Never contains a credential.
     */
    private val _providerStatus = MutableStateFlow(providerGateway.status())
    val providerStatus: StateFlow<ProviderStatus?> = _providerStatus.asStateFlow()

    val availableModels: StateFlow<List<AiModel>> =
        modelRepository.allModels
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Local, in-memory status refresh only — deliberately no network call.
        refreshProviderStatus()
    }

    fun setAutoRouting(enabled: Boolean) {
        _isAutoRouting.value = enabled
        apiKeyRepository.setAutoRoutingEnabled(enabled)
    }

    fun setDefaultModel(modelId: String) {
        _defaultModel.value = modelId
        apiKeyRepository.setDefaultModel(modelId)
        refreshProviderStatus()
    }

    fun refreshProviderStatus() {
        _providerStatus.value = providerGateway.status()
    }

    /**
     * User-triggered connection test.
     *
     * Never invoked automatically, so merely opening the app or this screen consumes no quota.
     */
    fun testConnection() {
        if (!providerGateway.isConfigured()) {
            _testState.value = ConnectionTestState.Error(
                message = "No HCNSEC API key is stored yet.",
                errorKind = ProviderErrorKind.NOT_CONFIGURED,
            )
            return
        }

        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            val result = providerGateway.testConnection(model = configuredModel())
            _testState.value = if (result.success) {
                ConnectionTestState.Success(latencyMillis = result.latencyMillis, model = result.model)
            } else {
                ConnectionTestState.Error(message = result.message, errorKind = result.errorKind)
            }
            refreshProviderStatus()
        }
    }

    fun updateApiKey(newKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            providerGateway.verifyEphemeralCredential(newKey).fold(
                onSuccess = {
                    if (apiKeyRepository.saveApiKey(newKey)) {
                        modelRepository.refreshModels()
                        refreshProviderStatus()
                        onSuccess()
                    } else {
                        onError("The key is valid but could not be stored securely on this device.")
                    }
                },
                onFailure = { err ->
                    onError(err.message ?: "Failed to validate new key")
                }
            )
        }
    }

    fun removeApiKey(onRemoved: () -> Unit) {
        apiKeyRepository.clearApiKey()
        _testState.value = ConnectionTestState.Idle
        refreshProviderStatus()
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

    /** The configured model, or null when only the "auto" router sentinel is set. */
    private fun configuredModel(): String? =
        _defaultModel.value.takeIf { it.isNotBlank() && it != HcnsecProviderConfig.AUTO_MODEL }

    class Factory(
        private val apiKeyRepository: ApiKeyRepository,
        private val providerGateway: HcnsecProviderGateway,
        private val modelRepository: ModelRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(apiKeyRepository, providerGateway, modelRepository) as T
        }
    }
}
