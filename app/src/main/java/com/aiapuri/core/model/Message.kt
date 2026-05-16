package com.aiapuri.core.model

import java.time.Instant
import java.util.UUID

/**
 * A single message within a conversation.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant = Instant.now(),
    val status: MessageStatus = MessageStatus.COMPLETE
)
