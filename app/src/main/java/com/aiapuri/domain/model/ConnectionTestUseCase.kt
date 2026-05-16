package com.aiapuri.domain.model

import com.aiapuri.core.model.ConnectionTestResult
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.core.util.ServerUrlValidator
import com.aiapuri.data.llama.DetailedHealthCheckResult
import com.aiapuri.data.llama.DetailedModelsResult
import com.aiapuri.data.llama.OkHttpLlamaApiClient

/**
 * Use case that tests the connection to a llama.cpp server.
 *
 * Steps:
 * 1. Validate the server URL
 * 2. Run a health check
 * 3. Fetch the model list
 *
 * Returns a [ConnectionTestResult] describing the outcome.
 */
class ConnectionTestUseCase {

    /**
     * Test the connection using the provided server settings.
     *
     * @param settings The server settings including URL and API key.
     * @return A [ConnectionTestResult] indicating success or failure details.
     */
    suspend operator fun invoke(settings: ServerSettings): ConnectionTestResult {
        // Step 1: Validate URL
        val urlResult = ServerUrlValidator.validate(settings.baseUrl)
        if (urlResult is ServerUrlValidator.Result.Invalid) {
            return ConnectionTestResult.InvalidUrl
        }

        val normalizedUrl = (urlResult as ServerUrlValidator.Result.Valid).normalizedUrl

        // Step 2: Create API client
        val client = OkHttpLlamaApiClient(
            baseUrl = normalizedUrl,
            apiKey = settings.apiKey
        )

        // Step 3: Health check
        val healthResult = client.detailedHealthCheck()
        val isHealthy = when (healthResult) {
            is DetailedHealthCheckResult.Success -> true
            is DetailedHealthCheckResult.Unauthorized -> {
                return ConnectionTestResult.Unauthorized
            }
            is DetailedHealthCheckResult.Unreachable -> {
                return ConnectionTestResult.Unreachable(healthResult.detail)
            }
            is DetailedHealthCheckResult.ServerError -> {
                return ConnectionTestResult.ServerError(healthResult.code, healthResult.message)
            }
        }

        if (!isHealthy) {
            return ConnectionTestResult.Unreachable("Health check failed")
        }

        // Step 4: Fetch model list
        val modelsResult = client.detailedListModels()
        return when (modelsResult) {
            is DetailedModelsResult.Success -> {
                ConnectionTestResult.Success(modelsResult.models)
            }
            DetailedModelsResult.Empty -> {
                // Server is healthy but returned no models — still a success
                ConnectionTestResult.Success(emptyList())
            }
            is DetailedModelsResult.Unauthorized -> {
                ConnectionTestResult.Unauthorized
            }
            is DetailedModelsResult.Unreachable -> {
                ConnectionTestResult.Unreachable(modelsResult.detail)
            }
            is DetailedModelsResult.ServerError -> {
                // Health succeeded but models endpoint errored — report with code
                ConnectionTestResult.ServerError(modelsResult.code, modelsResult.message)
            }
            is DetailedModelsResult.ParseError -> {
                // Server responded but response was malformed — still reachable
                ConnectionTestResult.ServerError(detail = "Failed to parse models response: ${modelsResult.detail}")
            }
        }
    }

    /**
     * Fetch the model list independently (e.g. for a refresh button).
     *
     * @param settings The server settings including URL and API key.
     * @return A list of available models, or empty list on failure.
     */
    suspend fun fetchModels(settings: ServerSettings): List<ModelInfo> {
        val urlResult = ServerUrlValidator.validate(settings.baseUrl)
        if (urlResult is ServerUrlValidator.Result.Invalid) {
            return emptyList()
        }

        val normalizedUrl = (urlResult as ServerUrlValidator.Result.Valid).normalizedUrl
        val client = OkHttpLlamaApiClient(
            baseUrl = normalizedUrl,
            apiKey = settings.apiKey
        )

        return client.listModels()
    }
}
