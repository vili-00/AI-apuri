package com.aiapuri.core.model

import java.time.Instant
import java.util.UUID

/**
 * A conversation between the user and the assistant.
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val model: String,
    val personaId: String? = null,
    val systemPromptSnapshot: String? = null
)
