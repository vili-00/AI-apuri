package com.aiapuri.domain.model

import com.aiapuri.core.model.ConnectionTestResult
import com.aiapuri.core.model.ServerSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ConnectionTestUseCase] using MockWebServer.
 *
 * Validates the full connection test flow: URL validation → health check → model fetch.
 */
class ConnectionTestUseCaseTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var useCase: ConnectionTestUseCase

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        useCase = ConnectionTestUseCase()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun serverUrl(): String {
        return mockServer.url("/").toString().removeSuffix("/")
    }

    // ==================== Full Success Flow ====================

    @Test
    fun `connection test returns Success with models on healthy server`() = runBlocking {
        // Health endpoint
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        // Models endpoint
        val modelsJson = """
            {
                "data": [
                    {"id": "model-1", "object": "model", "owned_by": "local"},
                    {"id": "model-2", "object": "model", "owned_by": "local"}
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(modelsJson))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)

        assertTrue(result is ConnectionTestResult.Success)
        val models = (result as ConnectionTestResult.Success).models
        assertEquals(2, models.size)
        assertEquals("model-1", models[0].id)
        assertEquals("model-2", models[1].id)
    }

    @Test
    fun `connection test returns Success with empty models when no models available`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"data": []}"""))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result is ConnectionTestResult.Success)
        assertTrue((result as ConnectionTestResult.Success).models.isEmpty())
    }

    // ==================== Invalid URL ====================

    @Test
    fun `connection test returns InvalidUrl for empty URL`() = runBlocking {
        val settings = ServerSettings(
            baseUrl = "",
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result == ConnectionTestResult.InvalidUrl)
    }

    @Test
    fun `connection test returns InvalidUrl for ftp scheme`() = runBlocking {
        val settings = ServerSettings(
            baseUrl = "ftp://example.com",
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result == ConnectionTestResult.InvalidUrl)
    }

    // ==================== Unauthorized ====================

    @Test
    fun `connection test returns Unauthorized when health check fails with 401`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "wrong-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result == ConnectionTestResult.Unauthorized)
    }

    @Test
    fun `connection test returns Unauthorized when models endpoint fails with 401`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result == ConnectionTestResult.Unauthorized)
    }

    // ==================== Unreachable ====================

    @Test
    fun `connection test returns Unreachable when server disconnects`() = runBlocking {
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result is ConnectionTestResult.Unreachable)
    }

    // ==================== Server Error ====================

    @Test
    fun `connection test returns ServerError when health check returns 503`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result is ConnectionTestResult.ServerError)
        assertEquals(503, (result as ConnectionTestResult.ServerError).code)
    }

    @Test
    fun `connection test returns ServerError when models endpoint returns 500`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result is ConnectionTestResult.ServerError)
        assertEquals(500, (result as ConnectionTestResult.ServerError).code)
    }

    @Test
    fun `connection test returns ServerError when models response is malformed`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not valid json"))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = "test-key",
            allowNoApiKey = false,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result is ConnectionTestResult.ServerError)
    }

    // ==================== No API Key Mode ====================

    @Test
    fun `connection test works without API key when server allows it`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"data": [{"id": "model-x", "object": "model", "owned_by": "local"}]}"""))

        val settings = ServerSettings(
            baseUrl = serverUrl(),
            apiKey = null,
            allowNoApiKey = true,
            defaultModel = null
        )

        val result = useCase(settings)
        assertTrue(result is ConnectionTestResult.Success)
        assertEquals(1, (result as ConnectionTestResult.Success).models.size)
    }
}
