package com.aiapuri.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aiapuri.core.model.AppSettings
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.core.util.ServerUrlValidator

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
    val testConnectionState: TestConnectionState = TestConnectionState.Idle
)

/**
 * Placeholder states for the test-connection button (actual logic in Task 07).
 */
enum class TestConnectionState {
    Idle,
    Testing,
    Success,
    Error
}

/**
 * ViewModel that manages settings form state and persists via [SettingsRepository].
 */
class SettingsViewModel(
    private val saveSettings: suspend (ServerSettings) -> Unit,
    private val saveAppSettings: suspend (AppSettings) -> Unit
) {

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

    /**
     * Save current form values to persistent storage.
     * Returns true if save is valid, false otherwise.
     */
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

    /** Placeholder for connection test (actual logic in Task 07). */
    fun testConnection() {
        uiState = uiState.copy(testConnectionState = TestConnectionState.Testing)
        // In Task 07 this will trigger an actual network test.
        // For now, just simulate idle after a brief delay.
    }
}
