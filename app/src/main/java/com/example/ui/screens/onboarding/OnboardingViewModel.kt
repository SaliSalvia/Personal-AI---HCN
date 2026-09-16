package com.example.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.HcnsecApiClient
import com.example.data.repository.ModelRepository
import com.example.data.security.ApiKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingValidationState {
    object Idle : OnboardingValidationState()
    object Validating : OnboardingValidationState()
    data class Success(val modelCount: Int) : OnboardingValidationState()
    data class Error(val message: String) : OnboardingValidationState()
}

class OnboardingViewModel(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiClient: HcnsecApiClient,
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

    fun validateAndConnect(onSuccess: () -> Unit) {
        val key = _apiKeyInput.value.trim()
        if (key.isEmpty()) {
            _validationState.value = OnboardingValidationState.Error("Please enter your HCNSEC API key")
            return
        }

        viewModelScope.launch {
            _validationState.value = OnboardingValidationState.Validating

            val result = apiClient.validateApiKey(key)
            result.fold(
                onSuccess = { models ->
                    // Securely save via Android Keystore
                    apiKeyRepository.saveApiKey(key)
                    // Fetch models to cache
                    modelRepository.refreshModels()
                    _validationState.value = OnboardingValidationState.Success(models.size)
                    onSuccess()
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
        private val apiClient: HcnsecApiClient,
        private val modelRepository: ModelRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(apiKeyRepository, apiClient, modelRepository) as T
        }
    }
}
