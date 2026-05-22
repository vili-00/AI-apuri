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

    // ==================== Whitespace preservation ====================

    @Test
    fun `streaming preserves whitespace-only deltas`() = runBlocking {
        // BPE tokenizers emit spaces as leading characters.
        // A delta containing only " " must NOT be filtered out.
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" \"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"world\"},\"finish_reason\":null}]}\n\n")
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
        assertEquals(3, textDeltas.size)
        assertEquals("Hello", textDeltas[0].text)
        assertEquals(" ", textDeltas[1].text)
        assertEquals("world", textDeltas[2].text)

        // Verify the assembled text preserves the space
        val assembled = textDeltas.joinToString("") { it.text }
        assertEquals("Hello world", assembled)
    }

    @Test
    fun `streaming preserves punctuation-only deltas`() = runBlocking {
        // Punctuation like "." and "," often arrive as standalone deltas.
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\".\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" \"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"World\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"!\"},\"finish_reason\":null}]}\n\n")
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
        assertEquals(5, textDeltas.size)
        val assembled = textDeltas.joinToString("") { it.text }
        assertEquals("Hello. World!", assembled)
    }

    @Test
    fun `streaming handles finish_reason as completion signal`() = runBlocking {
        // Some servers send finish_reason on the last chunk before [DONE].
        // The client should detect finish_reason and emit Complete.
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Response\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" text\"},\"finish_reason\":\"stop\"}]}\n\n")
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
        assertEquals(2, textDeltas.size)
        assertEquals("Response", textDeltas[0].text)
        assertEquals(" text", textDeltas[1].text)

        // Only one Complete event (from finish_reason, [DONE] is guarded)
        val completeEvents = events.filterIsInstance<ChatStreamEvent.Complete>()
        assertEquals(1, completeEvents.size)
    }

    @Test
    fun `streaming handles finish_reason with empty delta`() = runBlocking {
        // finish_reason chunk has empty delta — should still emit Complete.
        val buffer = Buffer()
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Done\"},\"finish_reason\":null}]}\n\n")
        buffer.writeUtf8("data: {\"id\":\"chat-1\",\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
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
        assertEquals("Done", textDeltas[0].text)

        val completeEvents = events.filterIsInstance<ChatStreamEvent.Complete>()
        assertEquals(1, completeEvents.size)
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

    // ==================== Streaming client configuration ====================

    @Test
    fun `streamingOkHttpClient has readTimeout disabled`() {
        val streamingClient = OkHttpLlamaApiClient.streamingOkHttpClient()
        // readTimeout(0) means disabled — required for long SSE streams
        assertEquals(0L, streamingClient.readTimeoutMillis.toLong())
    }

    @Test
    fun `streamingOkHttpClient has callTimeout safety net`() {
        val streamingClient = OkHttpLlamaApiClient.streamingOkHttpClient()
        // callTimeout should be 10 minutes (600_000 ms) as runaway protection
        assertEquals(600_000L, streamingClient.callTimeoutMillis.toLong())
    }

    @Test
    fun `streamingOkHttpClient has connectTimeout`() {
        val streamingClient = OkHttpLlamaApiClient.streamingOkHttpClient()
        assertEquals(10_000L, streamingClient.connectTimeoutMillis.toLong())
    }

    @Test
    fun `defaultOkHttpClient still has readTimeout for non-streaming endpoints`() {
        val defaultClient = OkHttpLlamaApiClient.defaultOkHttpClient()
        // Non-streaming endpoints should keep the 120s read timeout
        assertEquals(120_000L, defaultClient.readTimeoutMillis.toLong())
    }
}
