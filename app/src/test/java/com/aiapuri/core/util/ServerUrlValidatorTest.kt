package com.aiapuri.core.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ServerUrlValidator].
 *
 * Covers validation, normalization, and endpoint builder behavior.
 */
class ServerUrlValidatorTest {

    // ==================== Validation ====================

    @Test
    fun `validate accepts valid http URL`() {
        val result = ServerUrlValidator.validate("http://100.100.64.2:8080")
        assertTrue(result is ServerUrlValidator.Result.Valid)
    }

    @Test
    fun `validate accepts valid https URL`() {
        val result = ServerUrlValidator.validate("https://llama.example.com:8080")
        assertTrue(result is ServerUrlValidator.Result.Valid)
        assertEquals("https://llama.example.com:8080", (result as ServerUrlValidator.Result.Valid).normalizedUrl)
    }

    @Test
    fun `validate accepts URL without explicit port`() {
        val result = ServerUrlValidator.validate("http://192.168.1.50")
        assertTrue(result is ServerUrlValidator.Result.Valid)
    }

    @Test
    fun `validate strips trailing slashes`() {
        val result = ServerUrlValidator.validate("http://100.100.64.2:8080///")
        assertTrue(result is ServerUrlValidator.Result.Valid)
        assertEquals("http://100.100.64.2:8080", (result as ServerUrlValidator.Result.Valid).normalizedUrl)
    }

    @Test
    fun `validate trims whitespace`() {
        val result = ServerUrlValidator.validate("  http://100.100.64.2:8080  ")
        assertTrue(result is ServerUrlValidator.Result.Valid)
        assertEquals("http://100.100.64.2:8080", (result as ServerUrlValidator.Result.Valid).normalizedUrl)
    }

    @Test
    fun `validate rejects empty string`() {
        val result = ServerUrlValidator.validate("")
        assertTrue(result is ServerUrlValidator.Result.Invalid)
    }

    @Test
    fun `validate rejects whitespace-only string`() {
        val result = ServerUrlValidator.validate("   ")
        assertTrue(result is ServerUrlValidator.Result.Invalid)
    }

    @Test
    fun `validate rejects ftp scheme`() {
        val result = ServerUrlValidator.validate("ftp://example.com")
        assertTrue(result is ServerUrlValidator.Result.Invalid)
    }

    @Test
    fun `validate rejects file scheme`() {
        val result = ServerUrlValidator.validate("file:///local/path")
        assertTrue(result is ServerUrlValidator.Result.Invalid)
    }

    @Test
    fun `validate rejects malformed URL`() {
        val result = ServerUrlValidator.validate("not a url at all")
        assertTrue(result is ServerUrlValidator.Result.Invalid)
    }

    @Test
    fun `validate rejects URL with no host`() {
        val result = ServerUrlValidator.validate("http:///nohost")
        assertTrue(result is ServerUrlValidator.Result.Invalid)
    }

    @Test
    fun `validate accepts Tailscale-style IP`() {
        val result = ServerUrlValidator.validate("http://100.99.123.45:8080")
        assertTrue(result is ServerUrlValidator.Result.Valid)
        assertEquals("http://100.99.123.45:8080", (result as ServerUrlValidator.Result.Valid).normalizedUrl)
    }

    @Test
    fun `validate accepts localhost`() {
        val result = ServerUrlValidator.validate("http://localhost:8080")
        assertTrue(result is ServerUrlValidator.Result.Valid)
    }

    // ==================== Normalization ====================

    @Test
    fun `normalize strips single trailing slash`() {
        assertEquals("http://100.100.64.2:8080", ServerUrlValidator.normalize("http://100.100.64.2:8080/"))
    }

    @Test
    fun `normalize strips multiple trailing slashes`() {
        assertEquals("http://100.100.64.2:8080", ServerUrlValidator.normalize("http://100.100.64.2:8080////"))
    }

    @Test
    fun `normalize does not modify URL without trailing slash`() {
        assertEquals("http://100.100.64.2:8080", ServerUrlValidator.normalize("http://100.100.64.2:8080"))
    }

    @Test
    fun `normalize adds http scheme when missing`() {
        assertEquals("http://100.100.64.2:8080", ServerUrlValidator.normalize("100.100.64.2:8080"))
    }

    @Test
    fun `normalize trims leading and trailing whitespace`() {
        assertEquals("http://100.100.64.2:8080", ServerUrlValidator.normalize("  http://100.100.64.2:8080  "))
    }

    @Test
    fun `normalize preserves https scheme`() {
        assertEquals("https://secure.example.com", ServerUrlValidator.normalize("https://secure.example.com"))
    }

    // ==================== Endpoint builders ====================

    @Test
    fun `healthEndpoint appends health path`() {
        val base = "http://100.100.64.2:8080"
        assertEquals("http://100.100.64.2:8080/health", ServerUrlValidator.healthEndpoint(base))
    }

    @Test
    fun `modelsEndpoint appends v1 models path`() {
        val base = "http://100.100.64.2:8080"
        assertEquals("http://100.100.64.2:8080/v1/models", ServerUrlValidator.modelsEndpoint(base))
    }

    @Test
    fun `chatCompletionsEndpoint appends v1 chat completions path`() {
        val base = "http://100.100.64.2:8080"
        assertEquals("http://100.100.64.2:8080/v1/chat/completions", ServerUrlValidator.chatCompletionsEndpoint(base))
    }

    @Test
    fun `endpoints work with https base URL`() {
        val base = "https://llama.example.com:8080"
        assertEquals("https://llama.example.com:8080/health", ServerUrlValidator.healthEndpoint(base))
        assertEquals("https://llama.example.com:8080/v1/models", ServerUrlValidator.modelsEndpoint(base))
        assertEquals("https://llama.example.com:8080/v1/chat/completions", ServerUrlValidator.chatCompletionsEndpoint(base))
    }

    @Test
    fun `endpoints do not double-slash when base has no trailing slash`() {
        val base = "http://100.100.64.2:8080"
        // Should be exactly one slash between base and path
        val health = ServerUrlValidator.healthEndpoint(base)
        assertFalse("Endpoint should not contain double slash after port", health.contains("//8080/"))
        assertEquals("http://100.100.64.2:8080/health", health)
    }
}
