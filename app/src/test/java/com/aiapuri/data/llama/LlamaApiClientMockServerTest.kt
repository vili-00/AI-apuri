package com.aiapuri.data.llama

import com.aiapuri.core.model.ChatStreamEvent
import com.aiapuri.core.model.ModelInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Mock server tests for [OkHttpLlamaApiClient].
 *
 * Uses MockWebServer to simulate a llama.cpp server without requiring
 * a real server or internet connection.
 */
class LlamaApiClientMockServerTest {

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

    // ==================== Health Check ====================

    @Test
    fun `healthCheck returns true when server responds 200`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        assertTrue(client.healthCheck())
    }

    @Test
    fun `healthCheck returns false when server returns 500`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Error"))
        assertFalse(client.healthCheck())
    }

    @Test
    fun `healthCheck returns false when server is unreachable`() = runBlocking {
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertFalse(client.healthCheck())
    }

    @Test
    fun `detailedHealthCheck returns Success on 200`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        val result = client.detailedHealthCheck()
        assertTrue(result is DetailedHealthCheckResult.Success)
    }

    @Test
    fun `detailedHealthCheck returns Unauthorized on 401`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val result = client.detailedHealthCheck()
        assertTrue(result is DetailedHealthCheckResult.Unauthorized)
    }

    @Test
    fun `detailedHealthCheck returns Unreachable on disconnect`() = runBlocking {
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val result = client.detailedHealthCheck()
        assertTrue(result is DetailedHealthCheckResult.Unreachable)
    }

    @Test
    fun `detailedHealthCheck returns ServerError on 503`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val result = client.detailedHealthCheck()
        assertTrue(result is DetailedHealthCheckResult.ServerError)
        assertEquals(503, (result as DetailedHealthCheckResult.ServerError).code)
    }

    // ==================== List Models ====================

    @Test
    fun `listModels returns models on successful response`() = runBlocking {
        val json = """
            {
                "data": [
                    {"id": "model-a", "object": "model", "owned_by": "local"},
                    {"id": "model-b", "object": "model", "owned_by": "local"}
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val models = client.listModels()
        assertEquals(2, models.size)
        assertEquals("model-a", models[0].id)
        assertEquals("model-b", models[1].id)
    }

    @Test
    fun `listModels returns empty on 401`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val models = client.listModels()
        assertTrue(models.isEmpty())
    }

    @Test
    fun `detailedListModels returns Success with models`() = runBlocking {
        val json = """
            {
                "data": [
                    {"id": "qwen3.6-27b", "object": "model", "owned_by": "local"}
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = client.detailedListModels()
        assertTrue(result is DetailedModelsResult.Success)
        val models = (result as DetailedModelsResult.Success).models
        assertEquals(1, models.size)
        assertEquals("qwen3.6-27b", models[0].id)
    }

    @Test
    fun `detailedListModels returns Unauthorized on 401`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val result = client.detailedListModels()
        assertTrue(result is DetailedModelsResult.Unauthorized)
    }

    @Test
    fun `detailedListModels returns Unreachable on disconnect`() = runBlocking {
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val result = client.detailedListModels()
        assertTrue(result is DetailedModelsResult.Unreachable)
    }

    @Test
    fun `detailedListModels returns ServerError on 500`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        val result = client.detailedListModels()
        assertTrue(result is DetailedModelsResult.ServerError)
        assertEquals(500, (result as DetailedModelsResult.ServerError).code)
    }

    @Test
    fun `detailedListModels returns ParseError on malformed JSON`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))
        val result = client.detailedListModels()
        assertTrue(result is DetailedModelsResult.ParseError)
    }

    // ==================== Auth Header Behavior ====================

    @Test
    fun `requests include Authorization header when API key is set`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        client.healthCheck()

        val recordedRequest = mockServer.takeRequest()
        assertEquals("Bearer test-api-key", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `requests do not include Authorization header when API key is null`() = runBlocking {
        val noKeyClient = OkHttpLlamaApiClient(
            baseUrl = mockServer.url("/").toString().removeSuffix("/"),
            apiKey = null
        )
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        noKeyClient.healthCheck()

        val recordedRequest = mockServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }

    // ==================== Endpoint Behavior ====================

    @Test
    fun `healthCheck hits health endpoint`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        client.healthCheck()

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/health", recordedRequest.path)
    }

    @Test
    fun `listModels hits v1 models endpoint`() = runBlocking {
        val json = """{"data": []}"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))
        client.listModels()

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/v1/models", recordedRequest.path)
    }
}
