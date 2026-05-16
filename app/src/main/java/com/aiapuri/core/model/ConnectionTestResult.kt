package com.aiapuri.core.model

/**
 * Result of a connection test against the llama.cpp server.
 */
sealed class ConnectionTestResult {

    /** Server is reachable and responded successfully. */
    data class Success(
        val models: List<ModelInfo> = emptyList()
    ) : ConnectionTestResult()

    /** Server returned an authentication error. */
    object Unauthorized : ConnectionTestResult()

    /** Server could not be reached (network error, DNS, timeout). */
    data class Unreachable(val detail: String = "") : ConnectionTestResult()

    /** Server responded but returned an unexpected error. */
    data class ServerError(
        val code: Int? = null,
        val detail: String = ""
    ) : ConnectionTestResult()

    /** The URL format is invalid. */
    object InvalidUrl : ConnectionTestResult()
}
