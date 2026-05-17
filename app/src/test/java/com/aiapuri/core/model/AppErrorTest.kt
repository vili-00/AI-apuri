package com.aiapuri.core.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [AppError] sealed class.
 *
 * Verifies that each error type has correct user messages, retry flags,
 * and suggested actions.
 */
class AppErrorTest {

    // ==================== InvalidUrl ====================

    @Test
    fun `InvalidUrl has correct user message`() {
        val error = AppError.InvalidUrl
        assertEquals("Invalid server URL", error.userMessage)
    }

    @Test
    fun `InvalidUrl is not retryable`() {
        val error = AppError.InvalidUrl
        assertFalse(error.isRetryable)
    }

    @Test
    fun `InvalidUrl suggests opening settings`() {
        val error = AppError.InvalidUrl
        assertEquals(SuggestedAction.OpenSettings, error.suggestedAction)
    }

    @Test
    fun `InvalidUrl has no technical detail`() {
        val error = AppError.InvalidUrl
        assertNull(error.technicalDetail)
    }

    // ==================== Unreachable ====================

    @Test
    fun `Unreachable has correct user message`() {
        val error = AppError.Unreachable()
        assertTrue(error.userMessage.contains("Cannot reach server", ignoreCase = true))
    }

    @Test
    fun `Unreachable is retryable`() {
        val error = AppError.Unreachable()
        assertTrue(error.isRetryable)
    }

    @Test
    fun `Unreachable suggests retry`() {
        val error = AppError.Unreachable()
        assertEquals(SuggestedAction.Retry, error.suggestedAction)
    }

    @Test
    fun `Unreachable can carry technical detail`() {
        val error = AppError.Unreachable(technicalDetail = "Connection refused")
        assertEquals("Connection refused", error.technicalDetail)
    }

    // ==================== Unauthorized ====================

    @Test
    fun `Unauthorized has correct user message`() {
        val error = AppError.Unauthorized
        assertEquals("Authentication failed. Check your API key.", error.userMessage)
    }

    @Test
    fun `Unauthorized is not retryable`() {
        val error = AppError.Unauthorized
        assertFalse(error.isRetryable)
    }

    @Test
    fun `Unauthorized suggests opening settings`() {
        val error = AppError.Unauthorized
        assertEquals(SuggestedAction.OpenSettings, error.suggestedAction)
    }

    // ==================== ModelNotFound ====================

    @Test
    fun `ModelNotFound has correct user message`() {
        val error = AppError.ModelNotFound
        assertEquals("Model not found on server.", error.userMessage)
    }

    @Test
    fun `ModelNotFound is not retryable`() {
        val error = AppError.ModelNotFound
        assertFalse(error.isRetryable)
    }

    @Test
    fun `ModelNotFound suggests opening settings`() {
        val error = AppError.ModelNotFound
        assertEquals(SuggestedAction.OpenSettings, error.suggestedAction)
    }

    // ==================== ServerError ====================

    @Test
    fun `ServerError 500 is retryable`() {
        val error = AppError.ServerError(500)
        assertTrue(error.isRetryable)
        assertEquals(SuggestedAction.Retry, error.suggestedAction)
    }

    @Test
    fun `ServerError 503 is retryable`() {
        val error = AppError.ServerError(503)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `ServerError 429 is not retryable`() {
        val error = AppError.ServerError(429)
        assertFalse(error.isRetryable)
        assertEquals(SuggestedAction.Dismiss, error.suggestedAction)
    }

    @Test
    fun `ServerError 400 is not retryable`() {
        val error = AppError.ServerError(400)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `ServerError includes code in message`() {
        val error = AppError.ServerError(502)
        assertTrue(error.userMessage.contains("502"))
    }

    @Test
    fun `ServerError carries technical detail`() {
        val error = AppError.ServerError(500, technicalDetail = "Internal error")
        assertEquals("Internal error", error.technicalDetail)
    }

    // ==================== StreamingInterrupted ====================

    @Test
    fun `StreamingInterrupted with partial content has correct message`() {
        val error = AppError.StreamingInterrupted(partialContentKept = true)
        assertTrue(error.userMessage.contains("Partial response", ignoreCase = true))
    }

    @Test
    fun `StreamingInterrupted without partial content has correct message`() {
        val error = AppError.StreamingInterrupted(partialContentKept = false)
        assertTrue(error.userMessage.contains("No partial response", ignoreCase = true))
    }

    @Test
    fun `StreamingInterrupted is retryable`() {
        val error = AppError.StreamingInterrupted()
        assertTrue(error.isRetryable)
    }

    @Test
    fun `StreamingInterrupted suggests retry`() {
        val error = AppError.StreamingInterrupted()
        assertEquals(SuggestedAction.Retry, error.suggestedAction)
    }

    // ==================== Timeout ====================

    @Test
    fun `Timeout has correct user message`() {
        val error = AppError.Timeout
        assertTrue(error.userMessage.contains("timed out", ignoreCase = true))
    }

    @Test
    fun `Timeout is retryable`() {
        val error = AppError.Timeout
        assertTrue(error.isRetryable)
    }

    @Test
    fun `Timeout suggests retry`() {
        val error = AppError.Timeout
        assertEquals(SuggestedAction.Retry, error.suggestedAction)
    }

    // ==================== DatabaseError ====================

    @Test
    fun `DatabaseError has correct user message`() {
        val error = AppError.DatabaseError()
        assertTrue(error.userMessage.contains("data error", ignoreCase = true))
    }

    @Test
    fun `DatabaseError is retryable`() {
        val error = AppError.DatabaseError()
        assertTrue(error.isRetryable)
    }

    @Test
    fun `DatabaseError suggests dismiss`() {
        val error = AppError.DatabaseError()
        assertEquals(SuggestedAction.Dismiss, error.suggestedAction)
    }

    // ==================== Unknown ====================

    @Test
    fun `Unknown has default message`() {
        val error = AppError.Unknown()
        assertEquals("Unexpected error", error.userMessage)
    }

    @Test
    fun `Unknown allows custom message`() {
        val error = AppError.Unknown(userMessage = "Custom error")
        assertEquals("Custom error", error.userMessage)
    }

    @Test
    fun `Unknown is retryable by default`() {
        val error = AppError.Unknown()
        assertTrue(error.isRetryable)
    }

    @Test
    fun `Unknown allows non-retryable`() {
        val error = AppError.Unknown(isRetryable = false)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `Unknown suggests dismiss`() {
        val error = AppError.Unknown()
        assertEquals(SuggestedAction.Dismiss, error.suggestedAction)
    }

    // ==================== SuggestedAction enum ====================

    @Test
    fun `SuggestedAction has three values`() {
        val values = SuggestedAction.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(SuggestedAction.Retry))
        assertTrue(values.contains(SuggestedAction.OpenSettings))
        assertTrue(values.contains(SuggestedAction.Dismiss))
    }
}
