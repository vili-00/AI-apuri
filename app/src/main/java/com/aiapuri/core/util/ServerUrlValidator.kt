package com.aiapuri.core.util

import java.net.URL

/**
 * Validates and normalizes llama.cpp server base URLs.
 *
 * Accepts `http` and `https` schemes, strips trailing slashes,
 * and provides helpers for building endpoint URLs.
 */
object ServerUrlValidator {

    /**
     * Result of a URL validation attempt.
     */
    sealed class Result {
        data class Valid(val normalizedUrl: String) : Result()
        data class Invalid(val reason: String) : Result()
    }

    /**
     * Validate and normalize a server base URL.
     *
     * Rules:
     * - Must use `http` or `https` scheme
     * - Must have a valid host
     * - Trailing slashes are stripped
     * - Empty or whitespace-only input is rejected
     */
    fun validate(rawUrl: String): Result {
        val trimmed = rawUrl.trim()

        if (trimmed.isEmpty()) {
            return Result.Invalid("URL must not be empty")
        }

        return try {
            val url = URL(trimmed)

            when (url.protocol.lowercase()) {
                "http", "https" -> {
                    if (url.host.isEmpty()) {
                        Result.Invalid("URL must contain a valid host")
                    } else {
                        Result.Valid(normalize(trimmed))
                    }
                }

                else -> {
                    Result.Invalid("Only http and https schemes are supported")
                }
            }
        } catch (_: Exception) {
            Result.Invalid("URL format is invalid")
        }
    }

    /**
     * Normalize a URL string: ensure scheme, strip trailing slashes,
     * and strip any trailing /v1 path segment (the app appends it itself).
     */
    fun normalize(rawUrl: String): String {
        var url = rawUrl.trim()

        // Ensure scheme
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }

        // Strip trailing slashes
        while (url.length > 8 && url.endsWith("/")) {
            url = url.removeSuffix("/")
        }

        // Strip trailing /v1 so the app can safely append /v1/... endpoints
        if (url.endsWith("/v1", ignoreCase = true)) {
            url = url.removeSuffix("/v1")
        }

        return url
    }

    // --- Endpoint builders ---

    /**
     * Build the /health endpoint URL.
     */
    fun healthEndpoint(baseUrl: String): String {
        return "$baseUrl/health"
    }

    /**
     * Build the /v1/models endpoint URL.
     */
    fun modelsEndpoint(baseUrl: String): String {
        return "$baseUrl/v1/models"
    }

    /**
     * Build the /v1/chat/completions endpoint URL.
     */
    fun chatCompletionsEndpoint(baseUrl: String): String {
        return "$baseUrl/v1/chat/completions"
    }
}
