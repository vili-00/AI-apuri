package com.aiapuri.ui.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aiapuri.AiapuriApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * UI state for the diagnostics screen.
 */
data class DiagnosticsUiState(
    val isGenerating: Boolean = false,
    val reportText: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel for the diagnostics screen.
 *
 * Collects non-sensitive app information and generates a shareable report.
 */
class DiagnosticsViewModel(
    private val application: AiapuriApplication
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    var uiState by mutableStateOf(DiagnosticsUiState())
        private set

    /**
     * Generate the diagnostics report text.
     */
    fun generateReport() {
        uiState = uiState.copy(isGenerating = true, errorMessage = null, reportText = null)
        scope.launch {
            try {
                val report = com.aiapuri.core.util.DiagnosticsCollector.buildReport(
                    context = application,
                    application = application
                )
                uiState = uiState.copy(isGenerating = false, reportText = report)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isGenerating = false,
                    errorMessage = "Failed to generate diagnostics: ${e.message}"
                )
            }
        }
    }

    /** Clear the report text. */
    fun clearReport() {
        uiState = uiState.copy(reportText = null)
    }

    /** Dismiss error. */
    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
