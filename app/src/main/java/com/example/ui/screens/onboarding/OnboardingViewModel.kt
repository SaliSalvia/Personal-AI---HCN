package com.example.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.hcnsec.HcnsecProviderGateway
import com.example.data.repository.ModelRepository
import com.example.data.security.ApiKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingValidationState {
    data object Idle : OnboardingValidationState()
    data object Validating : OnboardingValidationState()
    data class Success(val modelCount: Int) : OnboardingValidationState()
    data class Error(val message: String) : OnboardingValidationState()
}

/**
 * First-run credential onboarding.
 *
 * The typed credential is validated through the provider registry/gateway (never by talking to
 * HTTP from the UI layer), stored through the hardware-backed credential repository, and dropped
 * from UI state as soon as it has been stored.
 */
class OnboardingViewModel(
    private val apiKeyRepository: ApiKeyRepository,
    private val providerGateway: HcnsecProviderGateway,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput = _apiKeyInput.asStateFlow()

    private val _validationState = MutableStateFlow<OnboardingValidationState>(OnboardingValidationState.Idle)
    val validationState = _validationState.asStateFlow()

    fun onApiKeyChanged(newKey: String) {
        _apiKeyInput.value = newKey
        if (_validationState.value is OnboardingValidationState.Error) {
            _validationState.value = OnboardingValidationState.Idle
        }
    }

    fun validateAndConnect(onConnected: () -> Unit) {
        val candidateKey = _apiKeyInput.value.trim()
        if (candidateKey.isEmpty()) {
            _validationState.value = OnboardingValidationState.Error("Please enter your HCNSEC API key")
            return
        }

        viewModelScope.launch {
            _validationState.value = OnboardingValidationState.Validating

            // Validated as an ephemeral credential: it is held in memory for this call only and is
            // never logged, persisted or attached to an error.
            providerGateway.verifyEphemeralCredential(candidateKey).fold(
                onSuccess = { models ->
                    if (!apiKeyRepository.saveApiKey(candidateKey)) {
                        _validationState.value = OnboardingValidationState.Error(
                            "The key is valid but could not be stored securely on this device, so nothing was saved."
                        )
                    } else {
                        // The credential now lives in secure storage only.
                        _apiKeyInput.value = ""
                        modelRepository.refreshModels()
                        _validationState.value = OnboardingValidationState.Success(models.size)
                        onConnected()
                    }
                },
                onFailure = { error ->
                    _validationState.value = OnboardingValidationState.Error(
                        error.message ?: "Validation failed. Please check your HCNSEC key and network connection."
                    )
                }
            )
        }
    }

    class Factory(
        private val apiKeyRepository: ApiKeyRepository,
        private val providerGateway: HcnsecProviderGateway,
        private val modelRepository: ModelRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(apiKeyRepository, providerGateway, modelRepository) as T
        }
    }
}
