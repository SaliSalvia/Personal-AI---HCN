package com.example.data.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Hardware-backed storage for the HCNSEC credential.
 *
 * This class and [KeystoreManager] are the only places in the app that touch Android secure
 * storage. Provider code depends on [ProviderCredentialSource] instead.
 *
 * Phase 4.2 security hardening: credentials are **only** ever written encrypted with
 * AndroidKeyStore AES-GCM. A previous plaintext fallback (used when the keystore was unavailable)
 * has been removed — if the credential cannot be encrypted it is not stored at all, so a secret
 * can no longer end up unencrypted on disk.
 */
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

        /** Prefix written by the removed plaintext fallback; only used to purge legacy values. */
        private const val LEGACY_PLAINTEXT_PREFIX = "PLAIN:"
    }

    /**
     * Saves the HCNSEC API key encrypted at rest via Android Keystore.
     *
     * @return true when the credential was encrypted and persisted. Returns false — writing
     *   nothing — when encryption is unavailable, so no plaintext credential is ever stored.
     */
    fun saveApiKey(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return false

        return try {
            val (ciphertext, iv) = keystoreManager.encrypt(trimmed)
            prefs.edit()
                .putString(KEY_CIPHERTEXT, ciphertext)
                .putString(KEY_IV, iv)
                .putLong(KEY_LAST_VALIDATED, System.currentTimeMillis())
                .apply()
            true
        } catch (_: Exception) {
            // Fail closed: never fall back to plaintext storage.
            false
        }
    }

    /**
     * Retrieves the decrypted HCNSEC API key, or null when none is stored / cannot be decrypted.
     */
    fun getApiKey(): String? {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null

        if (ciphertext.startsWith(LEGACY_PLAINTEXT_PREFIX)) {
            // A credential written by the old plaintext fallback is purged rather than read, so the
            // unencrypted secret does not remain on disk. The user is asked to re-enter the key.
            clearApiKey()
            return null
        }

        return try {
            keystoreManager.decrypt(ciphertext, iv)
        } catch (_: Exception) {
            null
        }
    }

    fun hasApiKey(): Boolean {
        val key = getApiKey()
        return !key.isNullOrBlank()
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_LAST_VALIDATED)
            .apply()
    }

    /**
     * Returns a masked representation of the API key for safe UI display (e.g. abcd••••••••wxyz).
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
