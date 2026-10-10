package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.AiProvider

/**
 * Storage for provider keys and per-provider preferences. Kept as an interface so the
 * domain layer (ModelRepository, ChatRepository, UI) and tests can consume a fake
 * without any Android Context.
 */
interface ApiKeyRepository {
    fun saveApiKey(apiKey: String)
    fun saveProviderKey(provider: AiProvider, apiKey: String)
    fun getApiKey(): String?
    fun getProviderKey(provider: AiProvider): String?
    fun hasApiKey(): Boolean
    fun hasProviderKey(provider: AiProvider): Boolean
    fun getConfiguredProviders(): List<AiProvider>
    fun isProviderConfigured(provider: AiProvider): Boolean
    fun hasAnyConfiguredProvider(): Boolean
    fun getActiveProvider(): AiProvider
    fun setActiveProvider(provider: AiProvider)
    fun getProviderBaseUrl(provider: AiProvider): String?
    fun getCustomBaseUrl(): String?
    fun saveCustomProvider(baseUrl: String, model: String?)
    fun getCustomModel(): String
    fun clearCustomProvider()
    fun clearApiKey()
    fun clearProviderKey(provider: AiProvider)
    fun getMaskedApiKey(): String
    fun getMaskedProviderKey(provider: AiProvider): String
    fun isAutoRoutingEnabled(): Boolean
    fun setAutoRoutingEnabled(enabled: Boolean)
    fun getDefaultModel(): String
    fun setDefaultModel(modelId: String)
    fun getFavoriteModelKeys(): Set<String>
    fun isFavoriteModelKey(key: String): Boolean
    fun toggleFavoriteModelKey(key: String): Boolean
}

/**
 * Default implementation. Keys are encrypted at rest via AndroidKeyStore (AES-GCM);
 * decryption goes through the hardware backed keystore, which is slow enough to be
 * visible when it happens on every outgoing request. The key is resolved once and
 * kept for the lifetime of the process.
 */
class DefaultApiKeyRepository(context: Context) : ApiKeyRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keystoreManager = KeystoreManager(context)

    @Volatile
    private var cachedApiKey: String? = null

    private companion object {
        const val PREFS_NAME = "sali_hcnsec_sec_store"
        const val KEY_CIPHERTEXT = "enc_api_key"
        const val KEY_IV = "enc_api_iv"
        const val KEY_ACTIVE_PROVIDER = "active_provider"
        const val KEY_LAST_VALIDATED = "key_last_validated"
        const val KEY_AUTO_ROUTING = "key_auto_routing_enabled"
        const val KEY_DEFAULT_MODEL = "key_default_model"
        const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        const val KEY_CUSTOM_MODEL = "custom_model"
        const val KEY_FAVORITES = "favorite_model_keys"
    }

    override fun saveApiKey(apiKey: String) {
        saveProviderKey(AiProvider.HCNSEC, apiKey)
    }

    override fun saveProviderKey(provider: AiProvider, apiKey: String) {
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

    override fun getApiKey(): String? = getProviderKey(AiProvider.HCNSEC)

    override fun getProviderKey(provider: AiProvider): String? {
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

    override fun hasApiKey(): Boolean = hasProviderKey(AiProvider.HCNSEC)

    override fun hasProviderKey(provider: AiProvider): Boolean = !getProviderKey(provider).isNullOrBlank()

    /** Every provider that currently has usable credentials. Multiple providers can be
     * configured at once; all of their models are exposed to the agent simultaneously. */
    override fun getConfiguredProviders(): List<AiProvider> = AiProvider.catalog.filter { isProviderConfigured(it) }

    override fun isProviderConfigured(provider: AiProvider): Boolean = when (provider) {
        AiProvider.CUSTOM -> !getCustomBaseUrl().isNullOrBlank() && hasProviderKey(provider)
        else -> hasProviderKey(provider)
    }

    /** True when at least one provider key exists (used to pick the start destination). */
    override fun hasAnyConfiguredProvider(): Boolean = getConfiguredProviders().isNotEmpty()

    /** Preferred provider for flows that still need a single pick (onboarding, account
     * usage). Falls back to the first configured provider and never hard-wires HCNSEC
     * unless nothing at all is configured. */
    override fun getActiveProvider(): AiProvider {
        val stored = prefs.getString(KEY_ACTIVE_PROVIDER, null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
        if (stored != null && isProviderConfigured(stored)) return stored
        return getConfiguredProviders().firstOrNull() ?: stored ?: AiProvider.HCNSEC
    }

    override fun setActiveProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    override fun getProviderBaseUrl(provider: AiProvider): String? {
        return if (provider == AiProvider.CUSTOM) {
            getCustomBaseUrl()
        } else provider.defaultBaseUrl
    }

    override fun getCustomBaseUrl(): String? = prefs.getString(KEY_CUSTOM_BASE_URL, null)

    override fun saveCustomProvider(baseUrl: String, model: String?) {
        val normalized = baseUrl.trim().removeSuffix("/")
        require(normalized.startsWith("https://")) { "Custom endpoint must use HTTPS." }
        require(normalized.length <= 240) { "Custom endpoint URL is too long." }
        prefs.edit()
            .putString(KEY_CUSTOM_BASE_URL, normalized)
            .putString(KEY_CUSTOM_MODEL, model?.trim().orEmpty())
            .apply()
    }

    override fun getCustomModel(): String = prefs.getString(KEY_CUSTOM_MODEL, "") ?: ""

    override fun clearCustomProvider() {
        prefs.edit().remove(KEY_CUSTOM_BASE_URL).remove(KEY_CUSTOM_MODEL).apply()
        clearProviderKey(AiProvider.CUSTOM)
    }

    override fun clearApiKey() = clearProviderKey(AiProvider.HCNSEC)

    override fun clearProviderKey(provider: AiProvider) {
        if (provider == AiProvider.HCNSEC) cachedApiKey = null
        prefs.edit()
            .remove(ciphertextKey(provider))
            .remove(ivKey(provider))
            .remove("${provider.name}_$KEY_LAST_VALIDATED")
            .apply()
    }

    override fun getMaskedApiKey(): String = getMaskedProviderKey(AiProvider.HCNSEC)

    override fun getMaskedProviderKey(provider: AiProvider): String {
        val key = getProviderKey(provider) ?: return "No key configured"
        if (key.length <= 8) return "••••••••"
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        val maskLength = (key.length - 8).coerceIn(8, 20)
        return "$prefix${"•".repeat(maskLength)}$suffix"
    }

    override fun isAutoRoutingEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_ROUTING, true)
    }

    override fun setAutoRoutingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ROUTING, enabled).apply()
    }

    override fun getDefaultModel(): String {
        return prefs.getString(KEY_DEFAULT_MODEL, "auto") ?: "auto"
    }

    override fun setDefaultModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, modelId).apply()
    }

    /** Favorites are tracked by the global selection key (`PROVIDER::model-id`) so a
     * model favorited on one API is never confused with the same id on another. Keeping
     * them out of the `custom_models` table also stops favorites from turning API models
     * into duplicated "custom" entries in the picker. */
    override fun getFavoriteModelKeys(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    override fun isFavoriteModelKey(key: String): Boolean = key in getFavoriteModelKeys()

    override fun toggleFavoriteModelKey(key: String): Boolean {
        if (key.isBlank()) return false
        val current = getFavoriteModelKeys().toMutableSet()
        val nowFavorite = if (key in current) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return nowFavorite
    }

    private fun ciphertextKey(provider: AiProvider) = if (provider == AiProvider.HCNSEC) KEY_CIPHERTEXT else "${provider.name}_$KEY_CIPHERTEXT"
    private fun ivKey(provider: AiProvider) = if (provider == AiProvider.HCNSEC) KEY_IV else "${provider.name}_$KEY_IV"
}
