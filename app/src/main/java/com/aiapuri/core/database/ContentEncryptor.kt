package com.aiapuri.core.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Field-level encryption for database content.
 *
 * Encrypts message content and other sensitive fields before they are
 * stored in Room. Uses an AES-GCM key from the Android Keystore.
 *
 * This provides encrypted-at-rest protection even without SQLCipher.
 */
class ContentEncryptor(
    context: Context
) {

    companion object {
        private const val KEY_ALIAS = "aiapuri_content_key"
    }

    private val secretKey: SecretKey = createOrRetrieveKey(context)

    /**
     * Encrypt a plaintext string. Returns null if input is null or blank.
     * Output format: base64(iv + ciphertext)
     */
    fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrBlank()) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedBytes.size)
        iv.copyInto(combined)
        encryptedBytes.copyInto(combined, iv.size)
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypt a previously encrypted string. Returns null on failure.
     */
    fun decrypt(encrypted: String?): String? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            val combined = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
            val plaintextBytes = cipher.doFinal(ciphertext)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun createOrRetrieveKey(context: Context): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
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
