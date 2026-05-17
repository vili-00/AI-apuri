package com.aiapuri.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.aiapuri.AiapuriApplication
import com.aiapuri.domain.model.ConnectionTestUseCase
import com.aiapuri.ui.components.SettingsForm
import kotlinx.coroutines.flow.first

/**
 * Settings screen for configuring the llama.cpp server connection.
 *
 * Lets the user edit server URL, API key, no-key mode, and default model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    application: AiapuriApplication,
    modifier: Modifier = Modifier
) {
    val viewModel = rememberSettingsViewModel(application, onNavigateToOnboarding)

    // Load current settings into the form
    LaunchedEffect(Unit) {
        val serverSettings = application.settingsRepository.serverSettingsFlow.first()
        val appSettings = application.settingsRepository.appSettingsFlow.first()
        viewModel.loadSettings(serverSettings, appSettings)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SettingsForm(
                viewModel = viewModel,
                onSaved = {
                    // Stay on settings screen after save — user can navigate back manually
                },
                showOnboardingHint = false
            )
        }
    }
}

/**
 * Create a SettingsViewModel wired to the application's SettingsRepository
 * and ConnectionTestUseCase.
 */
@Composable
fun rememberSettingsViewModel(
    application: AiapuriApplication,
    onNavigateToOnboarding: () -> Unit
): SettingsViewModel {
    val connectionTestUseCase = remember { ConnectionTestUseCase() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember {
        SettingsViewModel(
            saveSettings = { settings ->
                application.settingsRepository.saveServerSettings(settings)
            },
            saveAppSettings = { settings ->
                application.settingsRepository.saveAppSettings(settings)
            },
            testConnection = { serverSettings ->
                connectionTestUseCase(serverSettings)
            },
            clearAllDataUseCase = {
                // clearApplicationUserData() is synchronous and terminates
                // the app process. No coroutine needed.
                application.clearAllData()
            },
            onCleared = {
                // After clearing all data, navigate back to onboarding
                onNavigateToOnboarding()
            }
        )
    }
}
