package com.aiapuri.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aiapuri.core.model.AppSettings
import com.aiapuri.core.model.ConnectionTestResult
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.core.util.ServerUrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the settings / onboarding form.
 */
data class SettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val showApiKey: Boolean = false,
    val allowNoApiKey: Boolean = false,
    val defaultModel: String = "",
    val urlError: String? = null,
    val isSaving: Boolean = false,
    val testConnectionState: TestConnectionState = TestConnectionState.Idle,
    val fetchedModels: List<ModelInfo> = emptyList(),
    val connectionErrorMessage: String? = null
)

/**
 * States for the test-connection button.
 */
sealed class TestConnectionState {
    object Idle : TestConnectionState()
    object Testing : TestConnectionState()
    data class Success(val models: List<ModelInfo> = emptyList()) : TestConnectionState()
    data class Error(val message: String) : TestConnectionState()
}

/**
 * ViewModel that manages settings form state and persists via [SettingsRepository].
 */
class SettingsViewModel(
    private val saveSettings: suspend (ServerSettings) -> Unit,
    private val saveAppSettings: suspend (AppSettings) -> Unit,
    private val testConnection: suspend (ServerSettings) -> ConnectionTestResult = { _ ->
        // Default no-op for backward compatibility (e.g. tests without use case)
        ConnectionTestResult.Unreachable("Not configured")
    }
) {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    var uiState by mutableStateOf(SettingsUiState())
        private set

    /** Load saved settings into the form. */
    fun loadSettings(serverSettings: ServerSettings, appSettings: AppSettings) {
        uiState = uiState.copy(
            baseUrl = serverSettings.baseUrl,
            apiKey = serverSettings.apiKey ?: "",
            allowNoApiKey = serverSettings.allowNoApiKey,
            defaultModel = serverSettings.defaultModel ?: ""
        )
    }

    /** Update server URL (validates and stores error). */
    fun onBaseUrlChanged(newUrl: String) {
        val result = ServerUrlValidator.validate(newUrl)
        uiState = uiState.copy(
            baseUrl = newUrl,
            urlError = when (result) {
                is ServerUrlValidator.Result.Invalid -> result.reason
                is ServerUrlValidator.Result.Valid -> null
            }
        )
    }

    /** Update API key. */
    fun onApiKeyChanged(newKey: String) {
        uiState = uiState.copy(apiKey = newKey)
    }

    /** Toggle API key visibility. */
    fun toggleApiKeyVisibility() {
        uiState = uiState.copy(showApiKey = !uiState.showApiKey)
    }

    /** Update no-key development mode. */
    fun onAllowNoApiKeyChanged(allowed: Boolean) {
        uiState = uiState.copy(allowNoApiKey = allowed)
    }

    /** Update default model. */
    fun onDefaultModelChanged(model: String) {
        uiState = uiState.copy(defaultModel = model)
    }

    /** Select a model from the fetched list. */
    fun onSelectModel(model: ModelInfo) {
        uiState = uiState.copy(defaultModel = model.id)
    }

    /** Save current form values to persistent storage. */
    suspend fun save(): Boolean {
        val trimmedUrl = uiState.baseUrl.trim()
        val trimmedKey = uiState.apiKey.trim()

        // Validate: either URL + key, or URL + no-key mode
        if (trimmedUrl.isEmpty()) {
            uiState = uiState.copy(urlError = "Server URL is required")
            return false
        }

        if (trimmedKey.isEmpty() && !uiState.allowNoApiKey) {
            uiState = uiState.copy(urlError = "API key is required or enable no-key mode")
            return false
        }

        val urlResult = ServerUrlValidator.validate(trimmedUrl)
        if (urlResult is ServerUrlValidator.Result.Invalid) {
            uiState = uiState.copy(urlError = urlResult.reason)
            return false
        }

        uiState = uiState.copy(isSaving = true)

        val settings = ServerSettings(
            baseUrl = (urlResult as ServerUrlValidator.Result.Valid).normalizedUrl,
            apiKey = if (trimmedKey.isEmpty()) null else trimmedKey,
            allowNoApiKey = uiState.allowNoApiKey,
            defaultModel = uiState.defaultModel.takeIf { it.isNotBlank() }
        )

        saveSettings(settings)
        uiState = uiState.copy(isSaving = false)
        return true
    }

    /** Test connection to the llama.cpp server. */
    fun testConnection() {
        val trimmedUrl = uiState.baseUrl.trim()
        if (trimmedUrl.isEmpty()) {
            uiState = uiState.copy(
                testConnectionState = TestConnectionState.Error("Enter a server URL first"),
                connectionErrorMessage = "Enter a server URL first"
            )
            return
        }

        val urlResult = ServerUrlValidator.validate(trimmedUrl)
        if (urlResult is ServerUrlValidator.Result.Invalid) {
            uiState = uiState.copy(
                testConnectionState = TestConnectionState.Error(urlResult.reason),
                connectionErrorMessage = urlResult.reason
            )
            return
        }

        uiState = uiState.copy(
            testConnectionState = TestConnectionState.Testing,
            connectionErrorMessage = null
        )

        val settings = ServerSettings(
            baseUrl = (urlResult as ServerUrlValidator.Result.Valid).normalizedUrl,
            apiKey = uiState.apiKey.takeIf { it.isNotBlank() },
            allowNoApiKey = uiState.allowNoApiKey,
            defaultModel = uiState.defaultModel.takeIf { it.isNotBlank() }
        )

        ioScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { testConnection(settings) }
            } catch (e: Exception) {
                ConnectionTestResult.Unreachable(e.message ?: "Unexpected error")
            }

            when (result) {
                is ConnectionTestResult.Success -> {
                    uiState = uiState.copy(
                        testConnectionState = TestConnectionState.Success(result.models),
                        fetchedModels = result.models,
                        connectionErrorMessage = null
                    )
                    // If only one model and no default set, pre-fill it
                    if (result.models.size == 1 && uiState.defaultModel.isBlank()) {
                        uiState = uiState.copy(defaultModel = result.models.first().id)
                    }
                }
                ConnectionTestResult.Unauthorized -> {
                    uiState = uiState.copy(
                        testConnectionState = TestConnectionState.Error(
                            "Authentication failed. Check your API key."
                        ),
                        connectionErrorMessage = "Authentication failed. Check your API key."
                    )
                }
                is ConnectionTestResult.Unreachable -> {
                    uiState = uiState.copy(
                        testConnectionState = TestConnectionState.Error(
                            "Cannot reach server. ${result.detail.takeIf { it.isNotBlank() } ?: "Check URL and network."}"
                        ),
                        connectionErrorMessage = "Cannot reach server. ${result.detail.takeIf { it.isNotBlank() } ?: "Check URL and network."}"
                    )
                }
                is ConnectionTestResult.ServerError -> {
                    uiState = uiState.copy(
                        testConnectionState = TestConnectionState.Error(
                            "Server error ${result.code}. ${result.detail.takeIf { it.isNotBlank() } ?: ""}"
                        ),
                        connectionErrorMessage = "Server error ${result.code}. ${result.detail.takeIf { it.isNotBlank() } ?: ""}"
                    )
                }
                ConnectionTestResult.InvalidUrl -> {
                    uiState = uiState.copy(
                        testConnectionState = TestConnectionState.Error("Invalid server URL"),
                        connectionErrorMessage = "Invalid server URL"
                    )
                }
            }
        }
    }

    /** Refresh the model list without re-running the full connection test. */
    fun refreshModels() {
        val trimmedUrl = uiState.baseUrl.trim()
        val urlResult = ServerUrlValidator.validate(trimmedUrl)
        if (urlResult !is ServerUrlValidator.Result.Valid) return

        val settings = ServerSettings(
            baseUrl = urlResult.normalizedUrl,
            apiKey = uiState.apiKey.takeIf { it.isNotBlank() },
            allowNoApiKey = uiState.allowNoApiKey,
            defaultModel = uiState.defaultModel.takeIf { it.isNotBlank() }
        )

        ioScope.launch {
            val models = try {
                withContext(Dispatchers.IO) {
                    val client = com.aiapuri.data.llama.OkHttpLlamaApiClient(
                        baseUrl = urlResult.normalizedUrl,
                        apiKey = settings.apiKey
                    )
                    client.listModels()
                }
            } catch (_: Exception) {
                emptyList()
            }

            uiState = uiState.copy(
                fetchedModels = models,
                testConnectionState = if (models.isEmpty()) {
                    TestConnectionState.Error("No models returned by server")
                } else {
                    TestConnectionState.Success(models)
                }
            )
        }
    }
}
