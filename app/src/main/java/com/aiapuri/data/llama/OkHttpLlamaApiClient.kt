package com.aiapuri.data.llama

import com.aiapuri.core.model.ChatStreamEvent
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.core.util.ServerUrlValidator
import com.aiapuri.data.llama.dto.ChatCompletionChunk
import com.aiapuri.data.llama.dto.ChatCompletionRequest
import com.aiapuri.data.llama.dto.ChatCompletionResponse
import com.aiapuri.data.llama.dto.ModelsResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based implementation of [LlamaApiClient].
 *
 * Handles Bearer auth, JSON serialization, and SSE streaming.
 */
class OkHttpLlamaApiClient(
    private val baseUrl: String,
    private val apiKey: String?,
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) : LlamaApiClient {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }

    // ==================== Health check ====================

    override suspend fun healthCheck(): Boolean {
        val result = detailedHealthCheck()
        return result is DetailedHealthCheckResult.Success
    }

    /**
     * Detailed health check that returns status information.
     */
    suspend fun detailedHealthCheck(): DetailedHealthCheckResult {
        val url = ServerUrlValidator.healthEndpoint(baseUrl)
        return try {
            val request = buildGetRequest(url)
            val response = httpClient.newCall(request).execute()
            when {
                response.isSuccessful -> DetailedHealthCheckResult.Success
                response.code == 401 -> DetailedHealthCheckResult.Unauthorized(response.message)
                response.code == 403 -> DetailedHealthCheckResult.Unauthorized(response.message)
                response.code >= 500 -> DetailedHealthCheckResult.ServerError(response.code, response.message)
                else -> DetailedHealthCheckResult.ServerError(response.code, response.message)
            }
        } catch (e: Exception) {
            DetailedHealthCheckResult.Unreachable(e.message ?: "Connection failed")
        }
    }

    // ==================== List models ====================

    override suspend fun listModels(): List<ModelInfo> {
        val result = detailedListModels()
        return if (result is DetailedModelsResult.Success) result.models else emptyList()
    }

    /**
     * Detailed model list fetch that returns status information.
     */
    suspend fun detailedListModels(): DetailedModelsResult {
        val url = ServerUrlValidator.modelsEndpoint(baseUrl)
        val request = buildGetRequest(url)
        return try {
            val response = httpClient.newCall(request).execute()
            when {
                response.isSuccessful -> {
                    val body = response.body?.string() ?: return DetailedModelsResult.Empty
                    try {
                        val modelsResponse = json.decodeFromString<ModelsResponse>(body)
                        val models = modelsResponse.data.map { modelObj ->
                            ModelInfo(
                                id = modelObj.id,
                                displayName = modelObj.id
                            )
                        }
                        DetailedModelsResult.Success(models)
                    } catch (e: Exception) {
                        DetailedModelsResult.ParseError(e.message ?: "Failed to parse models response")
                    }
                }
                response.code == 401 -> DetailedModelsResult.Unauthorized(response.message)
                response.code == 403 -> DetailedModelsResult.Unauthorized(response.message)
                response.code >= 500 -> DetailedModelsResult.ServerError(response.code, response.message)
                else -> DetailedModelsResult.ServerError(response.code, response.message)
            }
        } catch (e: Exception) {
            DetailedModelsResult.Unreachable(e.message ?: "Connection failed")
        }
    }

    // ==================== Non-streaming chat ====================

    override suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
        val url = ServerUrlValidator.chatCompletionsEndpoint(baseUrl)
        val jsonBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = buildGetRequest(url)
            .newBuilder()
            .post(requestBody)

        val response = httpClient.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful) {
            throw LlamaApiException(
                code = response.code,
                message = response.body?.string() ?: "Request failed with code ${response.code}"
            )
        }

        val body = response.body?.string()
            ?: throw LlamaApiException(code = 0, message = "Empty response body")

        return json.decodeFromString<ChatCompletionResponse>(body)
    }

    // ==================== Streaming chat ====================

    override fun chatCompletionStream(request: ChatCompletionRequest): Flow<ChatStreamEvent> {
        val url = ServerUrlValidator.chatCompletionsEndpoint(baseUrl)
        val jsonBody = json.encodeToString(ChatCompletionRequest.serializer(), request.copy(stream = true))
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = buildGetRequest(url)
            .newBuilder()
            .post(requestBody)

        return callbackFlow {
            val eventSourceFactory = EventSources.createFactory(httpClient)

            val listener = object : EventSourceListener() {

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    // Skip the [DONE] sentinel
                    if (data == "[DONE]") {
                        trySend(ChatStreamEvent.Complete)
                        close()
                        return
                    }

                    try {
                        val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                        val content = chunk.choices
                            .firstOrNull()
                            ?.delta
                            ?.content

                        if (!content.isNullOrBlank()) {
                            trySend(ChatStreamEvent.TextDelta(content))
                        }
                    } catch (e: Exception) {
                        // Malformed chunk — ignore parse errors for unknown data lines
                        if (e.message?.contains("DONE") != true) {
                            // silently ignore
                        }
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?
                ) {
                    val code = response?.code
                    val message = t?.message ?: response?.message ?: "Streaming failed"

                    if (code != null && code >= 400) {
                        trySend(ChatStreamEvent.Error("Server error $code: $message", keepPartial = true))
                    } else {
                        trySend(ChatStreamEvent.Error(message, keepPartial = true))
                    }
                    close(t)
                }
            }

            val eventSource = eventSourceFactory.newEventSource(requestBuilder.build(), listener)

            awaitClose {
                eventSource.cancel()
            }
        }
    }

    // ==================== Auth helpers ====================

    /**
     * Build an authenticated GET Request.
     * Adds `Authorization: Bearer <key>` only when an API key is configured.
     */
    private fun buildGetRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .get()

        if (!apiKey.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }

        return builder.build()
    }
}

/**
 * Exception thrown when the llama.cpp API returns an error.
 */
class LlamaApiException(
    val code: Int,
    message: String
) : Exception(message)

// ==================== Detailed health check results ====================

/**
 * Detailed result from a health check, distinguishing between
 * success, auth failure, unreachable, and server errors.
 */
sealed class DetailedHealthCheckResult {
    object Success : DetailedHealthCheckResult()
    data class Unauthorized(val message: String) : DetailedHealthCheckResult()
    data class Unreachable(val detail: String) : DetailedHealthCheckResult()
    data class ServerError(val code: Int, val message: String) : DetailedHealthCheckResult()
}

// ==================== Detailed model list results ====================

/**
 * Detailed result from a model list fetch, distinguishing between
 * success, auth failure, unreachable, server errors, and parse failures.
 */
sealed class DetailedModelsResult {
    data class Success(val models: List<ModelInfo>) : DetailedModelsResult()
    object Empty : DetailedModelsResult()
    data class Unauthorized(val message: String) : DetailedModelsResult()
    data class Unreachable(val detail: String) : DetailedModelsResult()
    data class ServerError(val code: Int, val message: String) : DetailedModelsResult()
    data class ParseError(val detail: String) : DetailedModelsResult()
}
