package com.aiapuri.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.aiapuri.core.util.RedactingLog
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * Encrypts and decrypts short strings (e.g. API keys) using an AES-GCM
 * secret key stored in the Android Keystore.
 *
 * The key is created on first use and reused on subsequent launches.
 * Encrypted values are safe to store in SharedPreferences.
 */
class EncryptedStringStorage(
    private val context: Context
) {

    companion object {
        private const val KEY_ALIAS = "aiapuri_api_key_encryption"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val PREFS_NAME = "aiapuri_encrypted_prefs"
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretKey: SecretKey by lazy { createOrRetrieveKey() }

    // ==================== Public API ====================

    /**
     * Encrypt a plaintext string and persist it under [key].
     * Returns `null` if [plaintext] is `null` (nothing stored).
     */
    fun encryptAndStore(key: String, plaintext: String?): String? {
        if (plaintext == null) {
            prefs.edit().remove(key).apply()
            return null
        }
        val encrypted = encrypt(plaintext)
        prefs.edit().putString(key, encrypted).apply()
        return encrypted
    }

    /**
     * Retrieve and decrypt a previously stored string.
     * Returns `null` if nothing is stored under [key].
     */
    fun decryptAndRetrieve(key: String): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        return try {
            decrypt(encrypted)
        } catch (_: Exception) {
            // Decryption failure — clear stale data
            prefs.edit().remove(key).apply()
            null
        }
    }

    /**
     * Remove an encrypted value.
     */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ==================== Keystore key management ====================

    /**
     * Retrieve an existing key from the Android Keystore, or create one if it
     * does not yet exist.
     *
     * CRITICAL: Must NOT call KeyGenerator.generateKey() unconditionally.
     * generateKey() overwrites any existing key at the alias, destroying
     * all previously encrypted data.
     */
    private fun createOrRetrieveKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

        // Try to retrieve existing key first
        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey is SecretKey) {
            return existingKey
        }

        // Key does not exist yet — generate a new one
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    // ==================== Internal crypto ====================

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return combineIvAndCiphertext(iv, encryptedBytes)
    }

    private fun decrypt(encrypted: String): String {
        val (iv, encryptedBytes) = splitIvAndCiphertext(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        val plaintextBytes = cipher.doFinal(encryptedBytes)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun combineIvAndCiphertext(iv: ByteArray, ciphertext: ByteArray): String {
        val combined = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(combined)
        ciphertext.copyInto(combined, iv.size)
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    private fun splitIvAndCiphertext(encoded: String): Pair<ByteArray, ByteArray> {
        val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        return iv to ciphertext
    }
}
