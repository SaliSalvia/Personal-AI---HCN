package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * KeystoreManager handles hardware-backed cryptographic operations using AndroidKeyStore.
 * Keys are generated and retained securely inside the Android Keystore provider,
 * and data is encrypted using AES-GCM (256-bit).
 */
class KeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "sali_hcnsec_master_key"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string and returns a pair of (ciphertext Base64, IV Base64)
     */
    fun encrypt(plaintext: String): Pair<String, String> {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        return Pair(encryptedBase64, ivBase64)
    }

    /**
     * Decrypts ciphertext with IV and returns original plaintext string
     */
    fun decrypt(encryptedBase64: String, ivBase64: String): String {
        val secretKey = getOrCreateSecretKey()
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Removes the master key from Keystore if needed
     */
    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}
