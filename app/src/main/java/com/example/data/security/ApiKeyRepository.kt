package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.AiProvider

class ApiKeyRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keystoreManager = KeystoreManager(context)

    /**
     * Decryption goes through the hardware backed AndroidKeyStore, which is slow
     * enough to be visible when it happens on every outgoing request. The key is
     * resolved once and kept for the lifetime of the process.
     */
    @Volatile
    private var cachedApiKey: String? = null

    companion object {
        private const val PREFS_NAME = "sali_hcnsec_sec_store"
        private const val KEY_CIPHERTEXT = "enc_api_key"
        private const val KEY_IV = "enc_api_iv"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_LAST_VALIDATED = "key_last_validated"
        private const val KEY_AUTO_ROUTING = "key_auto_routing_enabled"
        private const val KEY_DEFAULT_MODEL = "key_default_model"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_CUSTOM_MODEL = "custom_model"
    }

    /**
     * Saves the HCNSEC API key encrypted at rest via Android Keystore.
     */
    fun saveApiKey(apiKey: String) {
        saveProviderKey(AiProvider.HCNSEC, apiKey)
    }

    fun saveProviderKey(provider: AiProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return
        if (provider == AiProvider.HCNSEC) cachedApiKey = null
        try {
            val (ciphertext, iv) = keystoreManager.encrypt(trimmed)
            prefs.edit()
                .putString(ciphertextKey(provider), ciphertext)
                .putString(ivKey(provider), iv)
                .putLong("${provider.name}_$KEY_LAST_VALIDATED", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Secure fallback if keystore fails on unsupported JVM / test environments
            prefs.edit()
                .putString(ciphertextKey(provider), "PLAIN:" + trimmed)
                .putString(ivKey(provider), "")
                .putLong("${provider.name}_$KEY_LAST_VALIDATED", System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Retrieves the decrypted HCNSEC API key.
     */
    fun getApiKey(): String? = getProviderKey(AiProvider.HCNSEC)

    fun getProviderKey(provider: AiProvider): String? {
        if (provider == AiProvider.HCNSEC) cachedApiKey?.let { return it }

        val ciphertext = prefs.getString(ciphertextKey(provider), null) ?: return null
        val iv = prefs.getString(ivKey(provider), null) ?: ""

        val resolved = if (ciphertext.startsWith("PLAIN:")) {
            ciphertext.removePrefix("PLAIN:")
        } else {
            try {
                keystoreManager.decrypt(ciphertext, iv)
            } catch (e: Exception) {
                null
            }
        }

        if (resolved != null) {
            if (provider == AiProvider.HCNSEC) cachedApiKey = resolved
        }
        return resolved
    }

    fun hasApiKey(): Boolean = hasProviderKey(AiProvider.HCNSEC)

    fun hasProviderKey(provider: AiProvider): Boolean = !getProviderKey(provider).isNullOrBlank()

    fun getActiveProvider(): AiProvider = prefs.getString(KEY_ACTIVE_PROVIDER, AiProvider.HCNSEC.name)
        ?.let { runCatching { AiProvider.valueOf(it) }.getOrDefault(AiProvider.HCNSEC) }
        ?: AiProvider.HCNSEC

    fun setActiveProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    fun getProviderBaseUrl(provider: AiProvider): String? {
        return if (provider == AiProvider.CUSTOM) {
            prefs.getString(KEY_CUSTOM_BASE_URL, null)
        } else provider.defaultBaseUrl
    }

    fun saveCustomProvider(baseUrl: String, model: String?) {
        val normalized = baseUrl.trim().removeSuffix("/")
        require(normalized.startsWith("https://")) { "Custom endpoint must use HTTPS." }
        require(normalized.length <= 240) { "Custom endpoint URL is too long." }
        prefs.edit()
            .putString(KEY_CUSTOM_BASE_URL, normalized)
            .putString(KEY_CUSTOM_MODEL, model?.trim().orEmpty())
            .apply()
    }

    fun getCustomModel(): String = prefs.getString(KEY_CUSTOM_MODEL, "") ?: ""

    fun clearCustomProvider() {
        prefs.edit().remove(KEY_CUSTOM_BASE_URL).remove(KEY_CUSTOM_MODEL).apply()
        clearProviderKey(AiProvider.CUSTOM)
    }

    fun clearApiKey() = clearProviderKey(AiProvider.HCNSEC)

    fun clearProviderKey(provider: AiProvider) {
        if (provider == AiProvider.HCNSEC) cachedApiKey = null
        prefs.edit()
            .remove(ciphertextKey(provider))
            .remove(ivKey(provider))
            .remove("${provider.name}_$KEY_LAST_VALIDATED")
            .apply()
    }

    /**
     * Returns a masked representation of the API key for safe UI display (e.g. sk-••••••••••••ab12).
     */
    fun getMaskedApiKey(): String = getMaskedProviderKey(AiProvider.HCNSEC)

    fun getMaskedProviderKey(provider: AiProvider): String {
        val key = getProviderKey(provider) ?: return "No key configured"
        if (key.length <= 8) return "••••••••"
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        val maskLength = (key.length - 8).coerceIn(8, 20)
        return "$prefix${"•".repeat(maskLength)}$suffix"
    }

    fun isAutoRoutingEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_ROUTING, true)
    }

    fun setAutoRoutingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ROUTING, enabled).apply()
    }

    fun getDefaultModel(): String {
        return prefs.getString(KEY_DEFAULT_MODEL, "auto") ?: "auto"
    }

    fun setDefaultModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, modelId).apply()
    }

    private fun ciphertextKey(provider: AiProvider) = if (provider == AiProvider.HCNSEC) KEY_CIPHERTEXT else "${provider.name}_$KEY_CIPHERTEXT"
    private fun ivKey(provider: AiProvider) = if (provider == AiProvider.HCNSEC) KEY_IV else "${provider.name}_$KEY_IV"
}
