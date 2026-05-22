package com.aiapuri.core.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.aiapuri.core.util.RedactingLog
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * Field-level encryption for database content.
 *
 * Encrypts message content and other sensitive fields before they are
 * stored in Room. Uses an AES-GCM key from the Android Keystore.
 *
 * Encrypted values are prefixed with [ENCRYPTED_PREFIX] so that legacy
 * plaintext rows and encrypted rows can be distinguished during read.
 *
 * This provides encrypted-at-rest protection even without SQLCipher.
 */
class ContentEncryptor(
    context: Context
) {

    companion object {
        private const val KEY_ALIAS = "aiapuri_content_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

        /**
         * Prefix prepended to every encrypted value stored in the database.
         * Format: aiapuri:v1:<base64(iv + ciphertext)>
         *
         * This allows the decrypt() method to distinguish encrypted values
         * from legacy plaintext rows.
         */
        const val ENCRYPTED_PREFIX = "aiapuri:v1:"

        /**
         * Safe placeholder returned when an encrypted message cannot be decrypted.
         * This is shown to the user instead of leaking ciphertext.
         */
        const val DECRYPT_ERROR_PLACEHOLDER = "[Encrypted message could not be decrypted]"
    }

    private val secretKey: SecretKey = createOrRetrieveKey(context)

    // ==================== Public API ====================

    /**
     * Encrypt a plaintext string.
     *
     * Returns null only if input is null or blank.
     * On encryption failure, throws an exception — never silently returns empty.
     *
     * Output format: `aiapuri:v1:<base64(iv + ciphertext)>`
     */
    fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrBlank()) return null

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            iv.copyInto(combined)
            encryptedBytes.copyInto(combined, iv.size)

            val base64Payload = android.util.Base64.encodeToString(
                combined,
                android.util.Base64.NO_WRAP
            )
            ENCRYPTED_PREFIX + base64Payload
        } catch (e: Exception) {
            // Encryption failure is a hard error — do not save empty string
            RedactingLog.e(e, "ContentEncryptor.encrypt failed")
            throw IllegalStateException("Failed to encrypt message content", e)
        }
    }

    /**
     * Decrypt a previously encrypted string.
     *
     * Behavior:
     * - If [encrypted] starts with [ENCRYPTED_PREFIX], attempt decryption.
     *   On failure, return [DECRYPT_ERROR_PLACEHOLDER].
     * - If [encrypted] does NOT start with the prefix, treat it as legacy
     *   plaintext and return it as-is.
     * - If [encrypted] is null or blank, return null.
     *
     * Guarantees:
     * - Ciphertext is NEVER returned to the caller.
     * - Legacy plaintext rows are preserved.
     */
    fun decrypt(encrypted: String?): String? {
        if (encrypted.isNullOrBlank()) return null

        // Legacy plaintext: no encrypted prefix → return as-is
        if (!encrypted.startsWith(ENCRYPTED_PREFIX)) {
            return encrypted
        }

        // Marked encrypted content — must decrypt
        val base64Payload = encrypted.removePrefix(ENCRYPTED_PREFIX)
        return try {
            val combined = android.util.Base64.decode(
                base64Payload,
                android.util.Base64.NO_WRAP
            )
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(128, iv)
            )
            val plaintextBytes = cipher.doFinal(ciphertext)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Decryption failure — return safe placeholder, never ciphertext
            RedactingLog.e(e, "ContentEncryptor.decrypt failed for message")
            DECRYPT_ERROR_PLACEHOLDER
        }
    }

    /**
     * Check whether a stored value appears to be encrypted (has the prefix).
     */
    fun isEncrypted(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(ENCRYPTED_PREFIX)
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
    private fun createOrRetrieveKey(context: Context): SecretKey {
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
}
