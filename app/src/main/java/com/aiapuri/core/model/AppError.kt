package com.aiapuri.core.model

/**
 * Central error model for AI-apuri.
 *
 * Every error path — network, database, streaming, settings — should produce
 * an [AppError]. The error carries:
 * - A human-readable message for the user
 * - Optional technical detail (redacted) for debugging
 * - Whether retry is possible
 * - A suggested action the user can take
 *
 * This replaces ad-hoc error strings and keeps error handling consistent
 * across all screens.
 */
sealed class AppError {

    /** The server URL is invalid or missing. */
    data object InvalidUrl : AppError() {
        override val userMessage: String = "Invalid server URL"
        override val technicalDetail: String? = null
        override val isRetryable: Boolean = false
        override val suggestedAction: SuggestedAction = SuggestedAction.OpenSettings
    }

    /** The server could not be reached (network, DNS, connection refused). */
    data class Unreachable(
        override val technicalDetail: String? = null
    ) : AppError() {
        override val userMessage: String =
            "Cannot reach server. Check your connection and server URL."
        override val isRetryable: Boolean = true
        override val suggestedAction: SuggestedAction = SuggestedAction.Retry
    }

    /** The server returned an authentication error (401/403). */
    data object Unauthorized : AppError() {
        override val userMessage: String =
            "Authentication failed. Check your API key."
        override val technicalDetail: String? = null
        override val isRetryable: Boolean = false
        override val suggestedAction: SuggestedAction = SuggestedAction.OpenSettings
    }

    /** The model was not found on the server. */
    data object ModelNotFound : AppError() {
        override val userMessage: String =
            "Model not found on server."
        override val technicalDetail: String? = null
        override val isRetryable: Boolean = false
        override val suggestedAction: SuggestedAction = SuggestedAction.OpenSettings
    }

    /** The server returned an HTTP error (4xx/5xx). */
    data class ServerError(
        val code: Int,
        override val technicalDetail: String? = null
    ) : AppError() {
        override val userMessage: String =
            when {
                code in 500..599 -> "Server error ($code). Please try again."
                code == 429 -> "Rate limited. Please try again later."
                else -> "Request failed ($code)"
            }
        override val isRetryable: Boolean = code >= 500
        override val suggestedAction: SuggestedAction =
            if (code >= 500) SuggestedAction.Retry else SuggestedAction.Dismiss
    }

    /** A streaming response was interrupted. */
    data class StreamingInterrupted(
        val partialContentKept: Boolean = true,
        override val technicalDetail: String? = null
    ) : AppError() {
        override val userMessage: String =
            if (partialContentKept) {
                "Streaming interrupted. Partial response is available below."
            } else {
                "Streaming interrupted. No partial response."
            }
        override val isRetryable: Boolean = true
        override val suggestedAction: SuggestedAction = SuggestedAction.Retry
    }

    /** A request timed out. */
    data object Timeout : AppError() {
        override val userMessage: String =
            "Request timed out. The server may be busy."
        override val technicalDetail: String? = null
        override val isRetryable: Boolean = true
        override val suggestedAction: SuggestedAction = SuggestedAction.Retry
    }

    /** A local database operation failed. */
    data class DatabaseError(
        override val technicalDetail: String? = null
    ) : AppError() {
        override val userMessage: String = "Local data error. Please try again."
        override val isRetryable: Boolean = true
        override val suggestedAction: SuggestedAction = SuggestedAction.Dismiss
    }

    /** An unexpected error with no specific category. */
    data class Unknown(
        override val userMessage: String = "Unexpected error",
        override val technicalDetail: String? = null,
        override val isRetryable: Boolean = true
    ) : AppError() {
        override val suggestedAction: SuggestedAction = SuggestedAction.Dismiss
    }

    // ==================== Common properties ====================

    /** Human-readable message shown to the user. */
    abstract val userMessage: String

    /**
     * Optional technical detail for debugging.
     * Always redacted — never contains API keys, chat content, or secrets.
     */
    abstract val technicalDetail: String?

    /** Whether the user can retry the failed operation. */
    abstract val isRetryable: Boolean

    /** Suggested action for the user. */
    abstract val suggestedAction: SuggestedAction
}

/**
 * Actions the user can take after an error.
 */
enum class SuggestedAction {
    /** Retry the operation. */
    Retry,
    /** Open the settings screen to fix configuration. */
    OpenSettings,
    /** Just dismiss the error. */
    Dismiss
}
