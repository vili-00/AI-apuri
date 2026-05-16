package com.aiapuri.core.model

import java.time.Instant

/**
 * Lightweight summary of a conversation for list display.
 * Avoids loading full message history.
 */
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Instant,
    val lastMessagePreview: String? = null,
    val model: String? = null,
    val personaName: String? = null
)
