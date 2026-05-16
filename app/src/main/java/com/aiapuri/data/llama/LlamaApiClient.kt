package com.aiapuri.data.llama

import com.aiapuri.core.model.ChatStreamEvent
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.data.llama.dto.ChatCompletionChunk
import com.aiapuri.data.llama.dto.ChatCompletionRequest
import com.aiapuri.data.llama.dto.ChatCompletionResponse
import com.aiapuri.data.llama.dto.ModelsResponse
import kotlinx.coroutines.flow.Flow

/**
 * Client for the llama.cpp OpenAI-compatible API.
 *
 * Version 1 supports:
 * - GET /health (availability check)
 * - GET /v1/models (list available models)
 * - POST /v1/chat/completions (non-streaming)
 * - POST /v1/chat/completions (streaming, SSE)
 */
interface LlamaApiClient {

    /**
     * Test the /health endpoint. Returns true if the server responds 200.
     */
    suspend fun healthCheck(): Boolean

    /**
     * Fetch the list of available models from /v1/models.
     */
    suspend fun listModels(): List<ModelInfo>

    /**
     * Send a non-streaming chat completion request.
     */
    suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse

    /**
     * Send a streaming chat completion request.
     *
     * Emits [ChatStreamEvent.TextDelta] for each content chunk,
     * [ChatStreamEvent.Complete] on success,
     * [ChatStreamEvent.Stopped] on cancellation,
     * [ChatStreamEvent.Error] on failure.
     */
    fun chatCompletionStream(request: ChatCompletionRequest): Flow<ChatStreamEvent>
}
