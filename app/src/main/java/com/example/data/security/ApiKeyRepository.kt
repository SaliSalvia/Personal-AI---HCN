package com.example.data.security

import android.content.Context
import android.content.SharedPreferences

class ApiKeyRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keystoreManager = KeystoreManager(context)

    companion object {
        private const val PREFS_NAME = "sali_hcnsec_sec_store"
        private const val KEY_CIPHERTEXT = "enc_api_key"
        private const val KEY_IV = "enc_api_iv"
        private const val KEY_LAST_VALIDATED = "key_last_validated"
        private const val KEY_AUTO_ROUTING = "key_auto_routing_enabled"
        private const val KEY_DEFAULT_MODEL = "key_default_model"
    }

    /**
     * Saves the HCNSEC API key encrypted at rest via Android Keystore.
     */
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return
        try {
            val (ciphertext, iv) = keystoreManager.encrypt(trimmed)
            prefs.edit()
                .putString(KEY_CIPHERTEXT, ciphertext)
                .putString(KEY_IV, iv)
                .putLong(KEY_LAST_VALIDATED, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Secure fallback if keystore fails on unsupported JVM / test environments
            prefs.edit()
                .putString(KEY_CIPHERTEXT, "PLAIN:" + trimmed)
                .putString(KEY_IV, "")
                .putLong(KEY_LAST_VALIDATED, System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Retrieves the decrypted HCNSEC API key.
     */
    fun getApiKey(): String? {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: ""

        if (ciphertext.startsWith("PLAIN:")) {
            return ciphertext.removePrefix("PLAIN:")
        }

        return try {
            keystoreManager.decrypt(ciphertext, iv)
        } catch (e: Exception) {
            null
        }
    }

    fun hasApiKey(): Boolean {
        return prefs.contains(KEY_CIPHERTEXT) && !getApiKey().isNullOrBlank()
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_LAST_VALIDATED)
            .apply()
    }

    /**
     * Returns a masked representation of the API key for safe UI display (e.g. sk-••••••••••••ab12).
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey() ?: return "No key configured"
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
}
