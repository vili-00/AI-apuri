package com.aiapuri.data.llama.dto

import kotlinx.serialization.Serializable

/**
 * A single SSE chunk from a streaming /v1/chat/completions response.
 *
 * The raw server sends lines like:
 *   data: {"id":"...","choices":[{"delta":{"role":"assistant","content":"hello"},...}]}
 *   data: [DONE]
 */
@Serializable
data class ChatCompletionChunk(
    val id: String,
    val model: String,
    val choices: List<DeltaChoice>
)

@Serializable
data class DeltaChoice(
    val index: Int,
    val delta: DeltaMessage,
    val finishReason: String? = null
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null
)
