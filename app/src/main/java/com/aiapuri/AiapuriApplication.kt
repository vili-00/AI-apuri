package com.aiapuri

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.conversation.DatabaseConversationRepository
import com.aiapuri.data.persona.DatabasePersonaRepository
import com.aiapuri.data.persona.PersonaRepository
import com.aiapuri.data.settings.DataStoreSettingsRepository
import com.aiapuri.data.settings.SettingsRepository
import com.aiapuri.domain.persona.PersonaSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AI-apuri Application entry point.
 * Holds singleton instances of repositories and the database.
 * Future tasks will migrate this to proper DI (Hilt/Koin).
 */
class AiapuriApplication : Application() {

    /** Room database — initialized lazily on first access. */
    val database: AiapuriDatabase by lazy {
        AiapuriDatabase.build(this)
    }

    /** Settings repository — initialized lazily on first access. */
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(this)
    }

    /** Conversation repository — backed by Room database. */
    val conversationRepository: ConversationRepository by lazy {
        DatabaseConversationRepository(database)
    }

    /** Persona repository — backed by Room database. */
    val personaRepository: PersonaRepository by lazy {
        DatabasePersonaRepository(database)
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize settings to catch Keystore errors early
        settingsRepository

        // Seed default personas on first launch (no-op if personas already exist)
        CoroutineScope(Dispatchers.IO).launch {
            PersonaSeeder(personaRepository).seedIfEmpty()
        }
    }

    /**
     * Clear all locally stored data using Android's built-in app data reset API.
     *
     * This behaves like Settings → Apps → AI-apuri → Storage → Clear data.
     * All conversations, messages, personas, server settings, API keys,
     * and app lock settings are wiped. The app process is terminated and
     * reopening the app shows the onboarding screen.
     */
    fun clearAllData() {
        val activityManager =
            getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.clearApplicationUserData()
    }
}

/**
 * Convenience extension to access the application instance.
 */
fun android.app.Activity.aiapuriApp(): AiapuriApplication {
    return application as AiapuriApplication
}
