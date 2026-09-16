package com.example.data.security

/**
 * A credential resolved for a single request.
 *
 * SECURITY: the value is never persisted, never logged and never copied into UI state.
 * [toString] is redacted so that an accidental string interpolation (for example in a log
 * message) cannot leak the secret.
 */
class ProviderCredential(val value: String) {

    val isBlank: Boolean get() = value.isBlank()

    override fun toString(): String = "ProviderCredential(redacted)"

    override fun equals(other: Any?): Boolean = other is ProviderCredential && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/**
 * The credential abstraction that provider code depends on.
 *
 * Provider adapters must depend on this interface and must never touch Android secure storage
 * (SharedPreferences / AndroidKeyStore) directly: [ApiKeyRepository] and [KeystoreManager] remain
 * the only classes in the app that access the hardware-backed store.
 */
interface ProviderCredentialSource {

    /** The stored credential, or null when none is configured. */
    fun storedCredential(): ProviderCredential?

    /** True when a stored credential exists. Never returns the credential itself. */
    fun hasCredential(): Boolean
}

/**
 * [ProviderCredentialSource] backed by the existing hardware-backed key store.
 *
 * This is the only bridge between the provider layer and secure storage, and it lives in the
 * security package so that provider code has no Android dependency at all.
 */
class StoredApiKeyCredentialSource(
    private val apiKeyRepository: ApiKeyRepository,
) : ProviderCredentialSource {

    override fun storedCredential(): ProviderCredential? {
        val stored = apiKeyRepository.getApiKey()?.takeIf { it.isNotBlank() } ?: return null
        return ProviderCredential(stored)
    }

    override fun hasCredential(): Boolean = apiKeyRepository.hasApiKey()
}
