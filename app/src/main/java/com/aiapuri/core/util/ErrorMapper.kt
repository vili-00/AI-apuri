package com.aiapuri.core.util

import com.aiapuri.core.model.AppError
import com.aiapuri.data.llama.LlamaApiException

/**
 * Central error mapper that converts raw exceptions, HTTP codes, and
 * raw error strings into [AppError] instances with redacted technical details.
 *
 * Guarantees:
 * - Never leaks API keys, chat content, or secrets in technical details.
 * - Truncates technical details to a safe length.
 * - Maps known error patterns to user-friendly messages.
 */
object ErrorMapper {

    private const val MAX_TECHNICAL_LENGTH = 200

    /**
     * Map a raw [Throwable] to an [AppError].
     *
     * Technical details are redacted and truncated.
     */
    fun map(throwable: Throwable): AppError {
        return when (throwable) {
            is LlamaApiException -> mapLlamaApiException(throwable)
            is java.net.ConnectException -> mapNetworkError(throwable)
            is java.net.SocketTimeoutException -> mapTimeout(throwable)
            is java.net.UnknownHostException -> mapDnsError(throwable)
            is java.net.SocketException -> mapNetworkError(throwable)
            else -> mapUnknown(throwable)
        }
    }

    /**
     * Map a raw HTTP status code to an [AppError].
     */
    fun mapHttpCode(code: Int, detail: String? = null): AppError {
        return when (code) {
            401, 403 -> AppError.Unauthorized
            404 -> AppError.ModelNotFound
            429 -> AppError.ServerError(429, redact(detail))
            in 500..599 -> AppError.ServerError(code, redact(detail))
            in 400..499 -> AppError.ServerError(code, redact(detail))
            else -> AppError.Unknown(
                userMessage = "Request failed ($code)",
                technicalDetail = redact(detail),
                isRetryable = true
            )
        }
    }

    /**
     * Map a raw error message from a streaming response to an [AppError].
     */
    fun mapStreamingMessage(rawMessage: String): AppError {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("unauthorized") || lower.contains("401") ->
                AppError.Unauthorized
            lower.contains("forbidden") || lower.contains("403") ->
                AppError.Unauthorized
            lower.contains("timeout") ->
                AppError.Timeout
            lower.contains("connection") || lower.contains("refused") || lower.contains("unreachable") ->
                AppError.Unreachable(technicalDetail = redact(rawMessage))
            lower.contains("not found") || lower.contains("model") ->
                AppError.ModelNotFound
            else ->
                AppError.StreamingInterrupted(
                    partialContentKept = true,
                    technicalDetail = redact(rawMessage)
                )
        }
    }

    // ==================== Internal mappers ====================

    private fun mapLlamaApiException(e: LlamaApiException): AppError {
        return when (e.code) {
            401, 403 -> AppError.Unauthorized
            404 -> AppError.ModelNotFound
            429 -> AppError.ServerError(429, redact(e.message))
            in 500..599 -> AppError.ServerError(e.code, redact(e.message))
            else -> AppError.ServerError(e.code, redact(e.message))
        }
    }

    private fun mapNetworkError(e: Throwable): AppError {
        return AppError.Unreachable(technicalDetail = redact(e.message))
    }

    private fun mapTimeout(e: Throwable): AppError {
        return AppError.Timeout
    }

    private fun mapDnsError(e: Throwable): AppError {
        return AppError.Unreachable(technicalDetail = "DNS resolution failed")
    }

    private fun mapUnknown(e: Throwable): AppError {
        return AppError.Unknown(
            userMessage = "Unexpected error",
            technicalDetail = redact("${e.javaClass.simpleName}: ${e.message}"),
            isRetryable = true
        )
    }

    /**
     * Redact sensitive data from a technical detail string.
     *
     * Strips:
     * - API key patterns (Bearer tokens, long hex strings)
     * - URL query parameters that look like keys
     * - Chat message content indicators
     *
     * Then truncates to [MAX_TECHNICAL_LENGTH].
     */
    private fun redact(text: String?): String? {
        if (text.isNullOrBlank()) return null

        var safe = text

        // Remove Bearer tokens
        safe = safe.replace(Regex("Bearer\\s+[A-Za-z0-9_\\-\\.]+"), "Bearer [REDACTED]")

        // Remove long hex strings that look like API keys (32+ hex chars)
        safe = safe.replace(Regex("[0-9a-fA-F]{32,}"), "[REDACTED]")

        // Remove URL query params that look like keys (key=..., token=..., password=...)
        safe = safe.replace(
            Regex("(key|token|password|secret|api[_-]?key)=[^&\\s]+", RegexOption.IGNORE_CASE),
            "$1=[REDACTED]"
        )

        // Remove content from chat messages that could leak prompts/responses
        safe = safe.replace(
            Regex("\"content\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE),
            "\"content\":\"[REDACTED]\""
        )

        return safe.take(MAX_TECHNICAL_LENGTH)
    }
}
