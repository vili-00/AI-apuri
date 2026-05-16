package com.aiapuri.core.model

import java.time.Instant

/**
 * A conversation together with all of its messages.
 */
data class ConversationWithMessages(
    val conversation: Conversation,
    val messages: List<Message>
)
