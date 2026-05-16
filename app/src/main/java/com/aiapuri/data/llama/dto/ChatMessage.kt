package com.aiapuri.data.llama.dto

import kotlinx.serialization.Serializable

/**
 * A single chat message in a request.
 */
@Serializable
data class ChatMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)
