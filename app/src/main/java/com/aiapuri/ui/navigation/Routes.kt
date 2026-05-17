package com.aiapuri.ui.navigation

/**
 * Navigation routes for the AI-apuri app.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val CONVERSATIONS = "conversations"

    // Graph route pattern (used by composable())
    const val CHAT = "chat/{conversationId}"

    // Concrete route prefix (used by navController.navigate())
    const val CHAT_ROUTE = "chat"

    const val CHAT_ARGS_CONVERSATION_ID = "conversationId"
    const val SETTINGS = "settings"
    const val PERSONAS = "personas"
}
