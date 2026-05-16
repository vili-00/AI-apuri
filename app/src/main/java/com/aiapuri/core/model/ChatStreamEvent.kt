package com.aiapuri.core.model

/**
 * Events emitted during a streaming chat response.
 */
sealed class ChatStreamEvent {

    /** A new text delta arrived from the server. */
    data class TextDelta(val text: String) : ChatStreamEvent()

    /** Streaming completed successfully. */
    object Complete : ChatStreamEvent()

    /** Streaming was cancelled by the user. */
    object Stopped : ChatStreamEvent()

    /** An error occurred during streaming. */
    data class Error(val message: String, val keepPartial: Boolean = true) : ChatStreamEvent()
}
