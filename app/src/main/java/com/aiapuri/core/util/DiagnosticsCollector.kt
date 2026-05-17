package com.aiapuri.core.util

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import com.aiapuri.AiapuriApplication
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.core.model.AppSettings
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Collects non-sensitive diagnostics data and writes it to a shareable text file.
 *
 * Guarantees:
 * - Never includes chat content, API keys, or encrypted data.
 * - Server URL is included but API key is always excluded.
 * - Conversation and persona counts are included but no actual data.
 */
object DiagnosticsCollector {

    /**
     * Collect diagnostics and write them to a file.
     *
     * @return The URI of the generated file, or null on failure.
     */
    suspend fun collectAndExport(context: Context, application: AiapuriApplication): android.net.Uri? {
        return try {
            val report = buildReport(context, application)
            val file = writeToFile(context, report)
            file
        } catch (e: Exception) {
            RedactingLog.e(e, "Diagnostics export failed")
            null
        }
    }

    /**
     * Build the diagnostics report as a string.
     */
    suspend fun buildReport(context: Context, application: AiapuriApplication): String {
        val sb = StringBuilder()
        sb.appendLine("=== AI-apuri Diagnostics Report ===")
        sb.appendLine("Generated: ${timestamp()}")
        sb.appendLine()

        // ---- Device info ----
        sb.appendLine("--- Device ---")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
        sb.appendLine("Android Release: ${Build.VERSION.RELEASE}")
        sb.appendLine()

        // ---- App info ----
        sb.appendLine("--- App ---")
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.appendLine("Package: ${pkgInfo.packageName}")
            sb.appendLine("Version name: ${pkgInfo.versionName}")
            sb.appendLine("Version code: ${pkgInfo.longVersionCode}")
        } catch (e: Exception) {
            sb.appendLine("Package info: unavailable")
        }
        sb.appendLine()

        // ---- Server settings (no API key) ----
        sb.appendLine("--- Server Settings ---")
        try {
            val serverSettings = application.settingsRepository.serverSettingsFlow.first()
            sb.appendLine("Server URL: ${serverSettings.baseUrl.ifBlank { "(not set)" }}")
            sb.appendLine("API key configured: ${if (serverSettings.apiKey.isNullOrBlank()) "No" else "Yes"}")
            sb.appendLine("No-key mode: ${serverSettings.allowNoApiKey}")
            sb.appendLine("Default model: ${serverSettings.defaultModel ?: "(not set)"}")
        } catch (e: Exception) {
            sb.appendLine("Server settings: unavailable (${e.javaClass.simpleName})")
        }
        sb.appendLine()

        // ---- App settings ----
        sb.appendLine("--- App Settings ---")
        try {
            val appSettings = application.settingsRepository.appSettingsFlow.first()
            sb.appendLine("Onboarding completed: ${appSettings.hasCompletedOnboarding}")
            sb.appendLine("App lock enabled: ${appSettings.appLockEnabled}")
            sb.appendLine("Screenshot blocking: ${appSettings.blockScreenshots}")
            sb.appendLine("Dark theme: ${appSettings.darkTheme ?: "system"}")
            sb.appendLine("Auto-generate titles: ${appSettings.autoGenerateTitles}")
        } catch (e: Exception) {
            sb.appendLine("App settings: unavailable (${e.javaClass.simpleName})")
        }
        sb.appendLine()

        // ---- Database stats (no content) ----
        sb.appendLine("--- Database Stats ---")
        try {
            val conversationCount = application.conversationRepository.countConversations()
            val personaCount = application.personaRepository.countPersonas()
            sb.appendLine("Conversations: $conversationCount")
            sb.appendLine("Personas: $personaCount")
        } catch (e: Exception) {
            sb.appendLine("Database stats: unavailable (${e.javaClass.simpleName})")
        }
        sb.appendLine()

        // ---- Encryption status ----
        sb.appendLine("--- Encryption ---")
        sb.appendLine("Database encrypted at rest: Yes (SQLCipher)")
        sb.appendLine("API key encrypted at rest: Yes (Android Keystore)")
        sb.appendLine()

        sb.appendLine("=== End of Diagnostics Report ===")
        return sb.toString()
    }

    /**
     * Write the report to a file in the app's external files directory.
     */
    private fun writeToFile(context: Context, report: String): android.net.Uri {
        val filename = "aiapuri-diagnostics-${timestamp()}.txt"
        val file = File(context.filesDir, filename)
        file.writeText(report)

        // Return a content URI via FileProvider for sharing
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Generate a timestamp string safe for filenames.
     */
    private fun timestamp(): String {
        val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
