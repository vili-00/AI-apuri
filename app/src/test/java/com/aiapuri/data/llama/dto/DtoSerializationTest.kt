package com.aiapuri.data.llama.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for OpenAI-compatible DTO serialization and deserialization.
 *
 * Verifies that request and response DTOs serialize/deserialize correctly
 * to match what the llama.cpp server expects and sends.
 */
class DtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ==================== ChatCompletionRequest ====================

    @Test
    fun `ChatCompletionRequest serializes with model and messages`() {
        val request = ChatCompletionRequest(
            model = "qwen3.6-27b",
            messages = listOf(
                ChatMessage(role = "system", content = "You are helpful."),
                ChatMessage(role = "user", content = "Hello!")
            ),
            stream = false
        )

        val serialized = json.encodeToString(
            ChatCompletionRequest.serializer(),
            request
        )

        assertTrue(serialized.contains("\"model\":\"qwen3.6-27b\""))
        assertTrue(serialized.contains("\"stream\":false"))
        assertTrue(serialized.contains("\"role\":\"system\""))
        assertTrue(serialized.contains("\"role\":\"user\""))
        assertTrue(serialized.contains("\"content\":\"Hello!\""))
    }

    @Test
    fun `ChatCompletionRequest serializes with stream true`() {
        val request = ChatCompletionRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
            stream = true
        )

        val serialized = json.encodeToString(
            ChatCompletionRequest.serializer(),
            request
        )

        assertTrue(serialized.contains("\"stream\":true"))
    }

    @Test
    fun `ChatCompletionRequest does not include tools field`() {
        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        val serialized = json.encodeToString(
            ChatCompletionRequest.serializer(),
            request
        )

        // Version 1 must not send tools
        assertFalse("Request must not contain 'tools'", serialized.contains("tools"))
    }

    // ==================== ChatCompletionResponse ====================

    @Test
    fun `ChatCompletionResponse deserializes from server JSON`() {
        val jsonStr = """
            {
                "id": "chatcmpl-123",
                "model": "qwen3.6-27b",
                "choices": [
                    {
                        "index": 0,
                        "message": { "role": "assistant", "content": "Hello there!" },
                        "finishReason": "stop"
                    }
                ],
                "usage": {
                    "promptTokens": 10,
                    "completionTokens": 5,
                    "totalTokens": 15
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<ChatCompletionResponse>(jsonStr)

        assertEquals("chatcmpl-123", response.id)
        assertEquals("qwen3.6-27b", response.model)
        assertEquals(1, response.choices.size)
        assertEquals("assistant", response.choices[0].message.role)
        assertEquals("Hello there!", response.choices[0].message.content)
        assertEquals("stop", response.choices[0].finishReason)
        assertNotNull(response.usage)
        assertEquals(15, response.usage?.totalTokens)
    }

    @Test
    fun `ChatCompletionResponse handles missing usage field`() {
        val jsonStr = """
            {
                "id": "chatcmpl-456",
                "model": "test",
                "choices": [
                    {
                        "index": 0,
                        "message": { "role": "assistant", "content": "OK" },
                        "finishReason": "stop"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ChatCompletionResponse>(jsonStr)

        assertNotNull(response)
        assertNull(response.usage)
    }

    // ==================== ModelsResponse ====================

    @Test
    fun `ModelsResponse deserializes model list`() {
        val jsonStr = """
            {
                "data": [
                    { "id": "model-a", "object": "model", "owned_by": "local" },
                    { "id": "model-b", "object": "model", "owned_by": "local" }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ModelsResponse>(jsonStr)

        assertEquals(2, response.data.size)
        assertEquals("model-a", response.data[0].id)
        assertEquals("model-b", response.data[1].id)
    }

    @Test
    fun `ModelsResponse handles empty model list`() {
        val jsonStr = """{ "data": [] }"""

        val response = json.decodeFromString<ModelsResponse>(jsonStr)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun `ModelsResponse handles missing optional fields`() {
        val jsonStr = """
            {
                "data": [
                    { "id": "minimal-model" }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ModelsResponse>(jsonStr)
        assertEquals("minimal-model", response.data[0].id)
        assertEquals("model", response.data[0].`object`) // default
        assertEquals("", response.data[0].ownedBy) // default
    }

    // ==================== ChatCompletionChunk (streaming) ====================

    @Test
    fun `ChatCompletionChunk deserializes streaming delta`() {
        val jsonStr = """
            {
                "id": "chatcmpl-stream-1",
                "model": "qwen3.6-27b",
                "choices": [
                    {
                        "index": 0,
                        "delta": { "role": "assistant", "content": "Hello" },
                        "finishReason": null
                    }
                ]
            }
        """.trimIndent()

        val chunk = json.decodeFromString<ChatCompletionChunk>(jsonStr)

        assertEquals("chatcmpl-stream-1", chunk.id)
        assertEquals("qwen3.6-27b", chunk.model)
        assertEquals(1, chunk.choices.size)
        assertEquals("assistant", chunk.choices[0].delta.role)
        assertEquals("Hello", chunk.choices[0].delta.content)
        assertNull(chunk.choices[0].finishReason)
    }

    @Test
    fun `ChatCompletionChunk handles content-only delta`() {
        val jsonStr = """
            {
                "id": "chatcmpl-stream-2",
                "model": "test",
                "choices": [
                    {
                        "index": 0,
                        "delta": { "content": " world" },
                        "finishReason": null
                    }
                ]
            }
        """.trimIndent()

        val chunk = json.decodeFromString<ChatCompletionChunk>(jsonStr)
        assertNull(chunk.choices[0].delta.role)
        assertEquals(" world", chunk.choices[0].delta.content)
    }

    @Test
    fun `ChatCompletionChunk handles finish reason`() {
        val jsonStr = """
            {
                "id": "chatcmpl-stream-3",
                "model": "test",
                "choices": [
                    {
                        "index": 0,
                        "delta": { "content": "" },
                        "finishReason": "stop"
                    }
                ]
            }
        """.trimIndent()

        val chunk = json.decodeFromString<ChatCompletionChunk>(jsonStr)
        assertEquals("stop", chunk.choices[0].finishReason)
    }

    // ==================== ApiErrorResponse ====================

    @Test
    fun `ApiErrorResponse deserializes error detail`() {
        val jsonStr = """
            {
                "error": {
                    "message": "Model not found",
                    "type": "invalid_request_error",
                    "param": "model",
                    "code": "model_not_found"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiErrorResponse>(jsonStr)

        assertNotNull(response.error)
        assertEquals("Model not found", response.error!!.message)
        assertEquals("invalid_request_error", response.error!!.type)
        assertEquals("model", response.error!!.param)
        assertEquals("model_not_found", response.error!!.code)
    }

    @Test
    fun `ApiErrorResponse handles null error detail`() {
        val jsonStr = """{ "error": null }"""

        val response = json.decodeFromString<ApiErrorResponse>(jsonStr)
        assertNull(response.error)
    }

    // ==================== ChatMessage ====================

    @Test
    fun `ChatMessage serializes correctly`() {
        val message = ChatMessage(role = "user", content = "Test message")

        val serialized = json.encodeToString(
            ChatMessage.serializer(),
            message
        )

        assertTrue(serialized.contains("\"role\":\"user\""))
        assertTrue(serialized.contains("\"content\":\"Test message\""))
    }

    @Test
    fun `ChatMessage handles all three roles`() {
        val systemMsg = ChatMessage(role = "system", content = "Be helpful.")
        val userMsg = ChatMessage(role = "user", content = "Hi")
        val assistantMsg = ChatMessage(role = "assistant", content = "Hello!")

        assertEquals("system", systemMsg.role)
        assertEquals("user", userMsg.role)
        assertEquals("assistant", assistantMsg.role)
    }
}
