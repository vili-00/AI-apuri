package com.aiapuri.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aiapuri.AiapuriApplication
import com.aiapuri.ui.components.SettingsForm
import com.aiapuri.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first

/**
 * Onboarding screen shown on first launch.
 *
 * Lets the user configure server URL, API key, and default model
 * before navigating to the conversation list.
 */
@Composable
fun OnboardingScreen(
    onNavigateToConversations: () -> Unit,
    application: AiapuriApplication,
    modifier: Modifier = Modifier
) {
    val viewModel = rememberOnboardingViewModel(application)

    // Load existing settings (in case user returned to onboarding)
    LaunchedEffect(Unit) {
        val serverSettings = application.settingsRepository.serverSettingsFlow.first()
        val appSettings = application.settingsRepository.appSettingsFlow.first()
        viewModel.loadSettings(serverSettings, appSettings)
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SettingsForm(
                viewModel = viewModel,
                onSaved = {
                    onNavigateToConversations()
                },
                showOnboardingHint = true
            )
        }
    }
}

/**
 * Create a SettingsViewModel wired to the application's SettingsRepository.
 */
@Composable
fun rememberOnboardingViewModel(application: AiapuriApplication): SettingsViewModel {
    return remember {
        SettingsViewModel(
            saveSettings = { settings ->
                application.settingsRepository.saveServerSettings(settings)
            },
            saveAppSettings = { settings ->
                application.settingsRepository.saveAppSettings(settings)
            }
        )
    }
}
