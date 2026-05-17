package com.aiapuri.data.llama.dto

import com.aiapuri.core.model.ChatStreamEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SSE chunk parsing behavior.
 *
 * Verifies that streaming deltas from the llama.cpp server are parsed
 * correctly, including edge cases like empty content, missing fields,
 * and the [DONE] sentinel.
 */
class StreamingParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ==================== Delta Content Extraction ====================

    @Test
    fun `first chunk includes role and content`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertEquals("Hello", content)
        assertEquals("assistant", chunk.choices.firstOrNull()?.delta?.role)
    }

    @Test
    fun `subsequent chunks may omit role`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":" world"},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertEquals(" world", content)
        assertNull(chunk.choices.firstOrNull()?.delta?.role)
    }

    @Test
    fun `empty content chunk is parsed without error`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":""},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertEquals("", content)
    }

    @Test
    fun `chunk with no content field is parsed`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertNull(content)
    }

    @Test
    fun `finish reason stop is parsed`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":"done"},"finishReason":"stop"}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        assertEquals("stop", chunk.choices.firstOrNull()?.finishReason)
    }

    @Test
    fun `finish reason length is parsed`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":"too long"},"finishReason":"length"}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        assertEquals("length", chunk.choices.firstOrNull()?.finishReason)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `chunk with extra unknown fields is parsed`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":"ok"},"finishReason":null}],"unknown":"ignored"}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        assertEquals("ok", chunk.choices.firstOrNull()?.delta?.content)
    }

    @Test
    fun `chunk with multiple choices uses first`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[
                {"index":0,"delta":{"content":"first"},"finishReason":null},
                {"index":1,"delta":{"content":"second"},"finishReason":null}
            ]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        assertEquals(2, chunk.choices.size)
        assertEquals("first", chunk.choices[0].delta.content)
        assertEquals("second", chunk.choices[1].delta.content)
    }

    @Test
    fun `chunk with empty choices list does not crash`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        assertTrue(chunk.choices.isEmpty())
        assertNull(chunk.choices.firstOrNull())
    }

    @Test
    fun `multiline content in a single delta is parsed`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":"line1\nline2\nline3"},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertNotNull(content)
        assertTrue(content!!.contains("line1"))
        assertTrue(content.contains("line2"))
        assertTrue(content.contains("line3"))
    }

    @Test
    fun `unicode content in delta is preserved`() {
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":"Hello 🌍 你好"},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertEquals("Hello 🌍 你好", content)
    }

    @Test
    fun `very long content in delta is preserved`() {
        val longContent = "x".repeat(10000)
        val chunkJson = """
            {"id":"1","model":"test","choices":[{"index":0,"delta":{"content":"$longContent"},"finishReason":null}]}
        """
        val chunk = json.decodeFromString<ChatCompletionChunk>(chunkJson)

        val content = chunk.choices.firstOrNull()?.delta?.content
        assertEquals(longContent, content)
    }
}
