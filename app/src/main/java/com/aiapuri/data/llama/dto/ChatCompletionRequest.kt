package com.aiapuri.data.llama.dto

import kotlinx.serialization.Serializable

/**
 * Request body for /v1/chat/completions.
 *
 * Version 1 does not include tools.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)
