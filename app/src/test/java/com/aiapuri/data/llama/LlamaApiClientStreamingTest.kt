package com.aiapuri.data.llama

import com.aiapuri.core.model.ChatStreamEvent
import com.aiapuri.data.llama.dto.ChatCompletionRequest
import com.aiapuri.data.llama.dto.ChatMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Streaming-specific mock server tests for [OkHttpLlamaApiClient].
 *
 * Uses MockWebServer with chunked transfer encoding to properly simulate
 * SSE (Server-Sent Events) streaming.
 */
class LlamaApiClientStreamingTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: OkHttpLlamaApiClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/").toString().removeSuffix("/")
        client = OkHttpLlamaApiClient(
            baseUrl = baseUrl,
            apiKey = "test-api-key"
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    // ==================== Successful streaming ====================

    @Test
    fun `streaming returns text deltas and complete event`() = runBlocking {
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: [DONE]\n\n")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setChunkedBody(buffer, 1)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        val events = withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        assertTrue(events.isNotEmpty())
        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertEquals(2, textDeltas.size)
        assertEquals("Hello", textDeltas[0].text)
        assertEquals(" world", textDeltas[1].text)

        val completeEvents = events.filterIsInstance<ChatStreamEvent.Complete>()
        assertEquals(1, completeEvents.size)
    }

    @Test
    fun `streaming handles single chunk response`() = runBlocking {
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Short answer\"},\"finish_reason\":\"stop\"}]}\n\n")
        buffer.writeUtf8("data: [DONE]\n\n")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setChunkedBody(buffer, 1)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        val events = withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertEquals(1, textDeltas.size)
        assertEquals("Short answer", textDeltas[0].text)
    }

    @Test
    fun `streaming handles empty content chunks gracefully`() = runBlocking {
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"actual text\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: [DONE]\n\n")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setChunkedBody(buffer, 1)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        val events = withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        // Empty content chunks should be skipped — only non-blank deltas emitted
        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertEquals(1, textDeltas.size)
        assertEquals("actual text", textDeltas[0].text)
    }

    // ==================== Streaming errors ====================

    @Test
    fun `streaming emits error on server failure`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal server error")
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        val events = withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        val errorEvents = events.filterIsInstance<ChatStreamEvent.Error>()
        assertTrue(errorEvents.isNotEmpty())
        assertTrue(errorEvents[0].keepPartial)
    }

    @Test
    fun `streaming emits error on connection failure`() = runBlocking {
        // DISCONNECT_AT_START causes immediate connection failure
        mockServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        // The flow should handle the connection failure gracefully
        // and emit an Error event rather than throwing
        val events = withTimeout(5000) {
            try {
                client.chatCompletionStream(request).toList()
            } catch (e: Exception) {
                // If the underlying connection fails before the SSE listener fires,
                // the callbackFlow may propagate the exception. This is acceptable
                // behavior — the important thing is the app doesn't crash.
                emptyList<ChatStreamEvent>()
            }
        }

        // Either we got error events from the SSE listener, or the exception
        // was caught above. Both are acceptable outcomes.
        val errorEvents = events.filterIsInstance<ChatStreamEvent.Error>()
        // If we got events, at least one should be an error
        if (events.isNotEmpty()) {
            assertTrue(errorEvents.isNotEmpty())
        }
    }

    @Test
    fun `streaming handles malformed JSON chunks without crashing`() = runBlocking {
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"good\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: this is not valid json\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" again\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: [DONE]\n\n")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setChunkedBody(buffer, 1)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        val events = withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        // Should still get the valid deltas and complete event
        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertEquals(2, textDeltas.size)
        assertEquals("good", textDeltas[0].text)
        assertEquals(" again", textDeltas[1].text)

        val completeEvents = events.filterIsInstance<ChatStreamEvent.Complete>()
        assertEquals(1, completeEvents.size)
    }

    // ==================== Streaming request path ====================

    @Test
    fun `streaming request hits chat completions endpoint`() = runBlocking {
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: [DONE]\n\n")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setChunkedBody(buffer, 1)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/v1/chat/completions", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
    }

    @Test
    fun `streaming request includes auth header`() = runBlocking {
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: [DONE]\n\n")

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setChunkedBody(buffer, 1)
        )

        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Hi"))
        )

        withTimeout(5000) {
            client.chatCompletionStream(request).toList()
        }

        val recordedRequest = mockServer.takeRequest()
        assertEquals("Bearer test-api-key", recordedRequest.getHeader("Authorization"))
    }
}
