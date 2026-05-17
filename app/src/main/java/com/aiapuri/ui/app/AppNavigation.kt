package com.aiapuri.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiapuri.AiapuriApplication
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.core.util.ServerUrlValidator
import com.aiapuri.ui.chat.ChatScreen
import com.aiapuri.ui.conversations.ConversationListScreen
import com.aiapuri.ui.navigation.Routes
import com.aiapuri.ui.lock.AppLockScreen
import com.aiapuri.ui.onboarding.OnboardingScreen
import com.aiapuri.ui.personas.PersonaScreen
import com.aiapuri.ui.diagnostics.DiagnosticsScreen
import com.aiapuri.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Startup state determining where the app should navigate on launch.
 */
private enum class StartupState {
    Loading,
    NeedsOnboarding,
    NeedsAppLock,
    Ready
}

/**
 * Check whether the user's server settings are sufficient to use the app.
 *
 * "Configured" means:
 * - Server base URL is present and valid
 * - API key is present, OR no-key development mode is enabled
 * - Default model is set
 */
private fun ServerSettings.isConfigured(): Boolean {
    if (baseUrl.isBlank()) return false
    if (ServerUrlValidator.validate(baseUrl) is ServerUrlValidator.Result.Invalid) return false
    if (apiKey.isNullOrBlank() && !allowNoApiKey) return false
    if (defaultModel.isNullOrBlank()) return false
    return true
}

/**
 * Root navigation graph for the app.
 *
 * On launch, the app checks saved server settings:
 * - If configured → conversations list
 * - If not configured → onboarding
 * A splash screen is shown briefly while settings are loaded to avoid flicker.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier
) {
    val application = rememberApplication()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var startupState by remember { mutableStateOf(StartupState.Loading) }

    // Load settings asynchronously on first compose
    androidx.compose.runtime.LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            scope.launch {
                val serverSettings = application.settingsRepository.serverSettingsFlow.first()
                val appSettings = application.settingsRepository.appSettingsFlow.first()

                startupState = when {
                    !serverSettings.isConfigured() -> StartupState.NeedsOnboarding
                    appSettings.appLockEnabled -> StartupState.NeedsAppLock
                    else -> StartupState.Ready
                }
            }
        }
    }

    when (startupState) {
        StartupState.Loading -> {
            // Splash screen while settings are loading
            Surface(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        StartupState.NeedsOnboarding -> {
            AppNavHost(
                startDestination = Routes.ONBOARDING,
                application = application,
                modifier = modifier
            )
        }

        StartupState.NeedsAppLock -> {
            AppLockScreen(
                onAuthenticated = {
                    startupState = StartupState.Ready
                },
                onDisableAppLock = {
                    scope.launch {
                        application.settingsRepository.saveAppSettings(
                            com.aiapuri.core.model.AppSettings(
                                hasCompletedOnboarding = true,
                                appLockEnabled = false,
                                blockScreenshots = application.settingsRepository.appSettingsFlow.first().blockScreenshots,
                                darkTheme = application.settingsRepository.appSettingsFlow.first().darkTheme
                            )
                        )
                        startupState = StartupState.Ready
                    }
                },
                modifier = modifier
            )
        }

        StartupState.Ready -> {
            AppNavHost(
                startDestination = Routes.CONVERSATIONS,
                application = application,
                modifier = modifier
            )
        }
    }
}

/**
 * Shared NavHost configuration.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AppNavHost(
    startDestination: String,
    application: AiapuriApplication,
    modifier: Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onNavigateToConversations = {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                application = application
            )
        }

        composable(Routes.CONVERSATIONS) {
            ConversationListScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate("${Routes.CHAT_ROUTE}/$conversationId")
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToPersonas = {
                    navController.navigate(Routes.PERSONAS)
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                androidx.navigation.navArgument(Routes.CHAT_ARGS_CONVERSATION_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString(Routes.CHAT_ARGS_CONVERSATION_ID)
            ChatScreen(
                conversationId = conversationId ?: "",
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = true }
                    }
                },
                onNavigateToDiagnostics = {
                    navController.navigate(Routes.DIAGNOSTICS)
                },
                application = application
            )
        }

        composable(Routes.PERSONAS) {
            PersonaScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Retrieve the [AiapuriApplication] instance from the Compose context.
 */
@Composable
private fun rememberApplication(): AiapuriApplication {
    val context = LocalContext.current
    return remember {
        context.applicationContext as AiapuriApplication
    }
}
