package com.aiapuri.core.model

/**
 * Lifecycle status of a Message.
 */
enum class MessageStatus {
    /** Message has been composed but not yet sent. */
    PENDING,

    /** Assistant response is currently streaming. */
    STREAMING,

    /** Message was sent and received fully. */
    COMPLETE,

    /** Streaming was cancelled by the user (Stop button). */
    STOPPED,

    /** Sending or receiving failed. */
    ERROR
}
