package com.aiapuri.core.util

import com.aiapuri.core.model.AppError
import com.aiapuri.data.llama.LlamaApiException
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [ErrorMapper].
 *
 * Verifies that raw exceptions, HTTP codes, and streaming error messages
 * are mapped to user-friendly [AppError] instances with redacted details.
 */
class ErrorMapperTest {

    // ==================== Throwable Mapping ====================

    @Test
    fun `map ConnectException returns Unreachable`() {
        val error = ErrorMapper.map(java.net.ConnectException("Connection refused"))
        assertTrue(error is AppError.Unreachable)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `map SocketTimeoutException returns Timeout`() {
        val error = ErrorMapper.map(java.net.SocketTimeoutException("Read timed out"))
        assertTrue(error is AppError.Timeout)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `map UnknownHostException returns Unreachable`() {
        val error = ErrorMapper.map(java.net.UnknownHostException("Unknown host"))
        assertTrue(error is AppError.Unreachable)
    }

    @Test
    fun `map SocketException returns Unreachable`() {
        val error = ErrorMapper.map(java.net.SocketException("Network reset"))
        assertTrue(error is AppError.Unreachable)
    }

    @Test
    fun `map LlamaApiException 401 returns Unauthorized`() {
        val error = ErrorMapper.map(LlamaApiException(401, "Unauthorized"))
        assertTrue(error is AppError.Unauthorized)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `map LlamaApiException 403 returns Unauthorized`() {
        val error = ErrorMapper.map(LlamaApiException(403, "Forbidden"))
        assertTrue(error is AppError.Unauthorized)
    }

    @Test
    fun `map LlamaApiException 404 returns ModelNotFound`() {
        val error = ErrorMapper.map(LlamaApiException(404, "Model not found"))
        assertTrue(error is AppError.ModelNotFound)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `map LlamaApiException 429 returns ServerError`() {
        val error = ErrorMapper.map(LlamaApiException(429, "Rate limited"))
        assertTrue(error is AppError.ServerError)
        assertEquals(429, (error as AppError.ServerError).code)
    }

    @Test
    fun `map LlamaApiException 500 returns ServerError`() {
        val error = ErrorMapper.map(LlamaApiException(500, "Internal error"))
        assertTrue(error is AppError.ServerError)
        assertEquals(500, (error as AppError.ServerError).code)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `map LlamaApiException 503 returns ServerError`() {
        val error = ErrorMapper.map(LlamaApiException(503, "Service unavailable"))
        assertTrue(error is AppError.ServerError)
        assertEquals(503, (error as AppError.ServerError).code)
    }

    @Test
    fun `map unknown exception returns Unknown`() {
        val error = ErrorMapper.map(RuntimeException("Something went wrong"))
        assertTrue(error is AppError.Unknown)
        assertTrue(error.isRetryable)
    }

    // ==================== HTTP Code Mapping ====================

    @Test
    fun `mapHttpCode 401 returns Unauthorized`() {
        val error = ErrorMapper.mapHttpCode(401)
        assertTrue(error is AppError.Unauthorized)
    }

    @Test
    fun `mapHttpCode 403 returns Unauthorized`() {
        val error = ErrorMapper.mapHttpCode(403)
        assertTrue(error is AppError.Unauthorized)
    }

    @Test
    fun `mapHttpCode 404 returns ModelNotFound`() {
        val error = ErrorMapper.mapHttpCode(404)
        assertTrue(error is AppError.ModelNotFound)
    }

    @Test
    fun `mapHttpCode 429 returns ServerError with correct code`() {
        val error = ErrorMapper.mapHttpCode(429)
        assertTrue(error is AppError.ServerError)
        assertEquals(429, (error as AppError.ServerError).code)
    }

    @Test
    fun `mapHttpCode 500 returns ServerError and is retryable`() {
        val error = ErrorMapper.mapHttpCode(500)
        assertTrue(error is AppError.ServerError)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `mapHttpCode 502 returns ServerError and is retryable`() {
        val error = ErrorMapper.mapHttpCode(502)
        assertTrue(error is AppError.ServerError)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `mapHttpCode 504 returns ServerError and is retryable`() {
        val error = ErrorMapper.mapHttpCode(504)
        assertTrue(error is AppError.ServerError)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `mapHttpCode 400 returns ServerError and is not retryable`() {
        val error = ErrorMapper.mapHttpCode(400)
        assertTrue(error is AppError.ServerError)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `mapHttpCode unknown code returns Unknown`() {
        val error = ErrorMapper.mapHttpCode(999)
        assertTrue(error is AppError.Unknown)
    }

    // ==================== Streaming Message Mapping ====================

    @Test
    fun `mapStreamingMessage with unauthorized returns Unauthorized`() {
        val error = ErrorMapper.mapStreamingMessage("401 Unauthorized")
        assertTrue(error is AppError.Unauthorized)
    }

    @Test
    fun `mapStreamingMessage with forbidden returns Unauthorized`() {
        val error = ErrorMapper.mapStreamingMessage("403 Forbidden")
        assertTrue(error is AppError.Unauthorized)
    }

    @Test
    fun `mapStreamingMessage with timeout returns Timeout`() {
        val error = ErrorMapper.mapStreamingMessage("Request timed out")
        assertTrue(error is AppError.Timeout)
    }

    @Test
    fun `mapStreamingMessage with connection error returns Unreachable`() {
        val error = ErrorMapper.mapStreamingMessage("Connection refused")
        assertTrue(error is AppError.Unreachable)
    }

    @Test
    fun `mapStreamingMessage with model not found returns ModelNotFound`() {
        val error = ErrorMapper.mapStreamingMessage("Model not found on server")
        assertTrue(error is AppError.ModelNotFound)
    }

    @Test
    fun `mapStreamingMessage with unknown error returns StreamingInterrupted`() {
        val error = ErrorMapper.mapStreamingMessage("Some weird error")
        assertTrue(error is AppError.StreamingInterrupted)
        assertTrue(error.isRetryable)
    }

    // ==================== Redaction ====================

    @Test
    fun `map redacts Bearer tokens from technical detail`() {
        val error = ErrorMapper.map(LlamaApiException(500, "Bearer abc123secret token"))
        val detail = error.technicalDetail
        assertNotNull(detail)
        assertFalse("Bearer token should be redacted", detail!!.contains("abc123secret"))
        assertTrue(detail.contains("[REDACTED]"))
    }

    @Test
    fun `map redacts long hex strings from technical detail`() {
        val hexKey = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4" // 32 hex chars
        val error = ErrorMapper.map(LlamaApiException(500, "Key: $hexKey"))
        val detail = error.technicalDetail
        assertNotNull(detail)
        assertFalse("Hex key should be redacted", detail!!.contains(hexKey))
    }

    @Test
    fun `map redacts API key from URL query params`() {
        val error = ErrorMapper.map(LlamaApiException(500, "api_key=mysecretkey123"))
        val detail = error.technicalDetail
        assertNotNull(detail)
        assertFalse("API key should be redacted", detail!!.contains("mysecretkey123"))
    }

    @Test
    fun `map truncates long technical details`() {
        val longDetail = "x".repeat(500)
        val error = ErrorMapper.map(LlamaApiException(500, longDetail))
        val detail = error.technicalDetail
        assertNotNull(detail)
        assertTrue("Detail should be truncated", detail!!.length <= 200)
    }

    @Test
    fun `map handles null message gracefully`() {
        val error = ErrorMapper.map(RuntimeException(null as String?))
        // Should not crash
        assertNotNull(error)
    }
}
