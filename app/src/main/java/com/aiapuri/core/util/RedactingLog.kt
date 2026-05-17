package com.aiapuri.core.util

import android.util.Log

/**
 * Safe logging wrapper that redacts sensitive data.
 *
 * Never logs:
 * - API keys or auth tokens
 * - Chat message content (prompts / responses)
 * - User identifiers
 *
 * Use this class for all debug logging in the app. Regular `Log` calls
 * are forbidden in production code to prevent accidental leakage.
 */
object RedactingLog {

    private const val TAG = "AIapuri"
    private const val REDACTED = "[REDACTED]"

    /**
     * Keywords that indicate sensitive content. If any are found in the
     * message, the entire message is suppressed.
     */
    private val sensitiveKeywords = listOf(
        "api_key", "apikey", "api-key", "bearer", "authorization",
        "password", "secret", "token",
        "content", "prompt", "response", "message",
        "conversation", "chat"
    )

    /**
     * Log a debug message only if it does not contain sensitive content.
     */
    fun d(message: String) {
        if (!containsSensitiveData(message)) {
            Log.d(TAG, message)
        }
    }

    /**
     * Log a warning message only if it does not contain sensitive content.
     */
    fun w(message: String) {
        if (!containsSensitiveData(message)) {
            Log.w(TAG, message)
        }
    }

    /**
     * Log an error message. Stack traces are always logged; the message
     * body is redacted if it contains sensitive content.
     */
    fun e(throwable: Throwable, message: String = "") {
        val safeMessage = if (containsSensitiveData(message)) REDACTED else message
        Log.e(TAG, safeMessage, throwable)
    }

    /**
     * Check whether a string looks like it contains sensitive data.
     */
    private fun containsSensitiveData(text: String): Boolean {
        val lower = text.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }
}
