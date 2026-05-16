package com.aiapuri.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiapuri.AiapuriApplication
import com.aiapuri.ui.chat.ChatScreen
import com.aiapuri.ui.conversations.ConversationListScreen
import com.aiapuri.ui.navigation.Routes
import com.aiapuri.ui.onboarding.OnboardingScreen
import com.aiapuri.ui.personas.PersonaScreen
import com.aiapuri.ui.settings.SettingsScreen

/**
 * Root navigation graph for the app.
 *
 * Onboarding is shown first. After the user completes onboarding (future task),
 * the app navigates to the conversation list.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    startDestination: String = Routes.ONBOARDING
) {
    val navController = rememberNavController()
    val application = rememberApplication()

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
                    navController.navigate("${Routes.CHAT}/$conversationId")
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
            val conversationId = backStackEntry.arguments?.getString(Routes.CHAT_ARGS_CONVERSATION_ID) ?: ""
            ChatScreen(
                conversationId = conversationId,
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
