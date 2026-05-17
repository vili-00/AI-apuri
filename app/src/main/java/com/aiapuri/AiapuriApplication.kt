package com.aiapuri

import android.app.Application
import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.database.ContentEncryptor
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.conversation.DatabaseConversationRepository
import com.aiapuri.data.persona.DatabasePersonaRepository
import com.aiapuri.data.persona.PersonaRepository
import com.aiapuri.data.settings.DataStoreSettingsRepository
import com.aiapuri.data.settings.SettingsRepository

/**
 * AI-apuri Application entry point.
 * Holds singleton instances of repositories and the encrypted database.
 * Future tasks will migrate this to proper DI (Hilt/Koin).
 */
class AiapuriApplication : Application() {

    /** Content encryptor for field-level encryption of message data. */
    val contentEncryptor: ContentEncryptor by lazy {
        ContentEncryptor(this)
    }

    /** Room database — initialized lazily on first access. */
    val database: AiapuriDatabase by lazy {
        AiapuriDatabase.build(this)
    }

    /** Settings repository — initialized lazily on first access. */
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(this)
    }

    /** Conversation repository — backed by encrypted database. */
    val conversationRepository: ConversationRepository by lazy {
        DatabaseConversationRepository(database, contentEncryptor)
    }

    /** Persona repository — backed by encrypted database. */
    val personaRepository: PersonaRepository by lazy {
        DatabasePersonaRepository(database)
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize settings to catch Keystore errors early
        settingsRepository
    }
}

/**
 * Convenience extension to access the application instance.
 */
fun android.app.Activity.aiapuriApp(): AiapuriApplication {
    return application as AiapuriApplication
}
