package com.aiapuri

import android.app.Application
import com.aiapuri.data.settings.DataStoreSettingsRepository
import com.aiapuri.data.settings.SettingsRepository

/**
 * AI-apuri Application entry point.
 * Holds singleton instances of repositories.
 * Future tasks will migrate this to proper DI (Hilt/Koin).
 */
class AiapuriApplication : Application() {

    /** Settings repository — initialized lazily on first access. */
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize to catch Keystore errors early
        settingsRepository
    }
}

/**
 * Convenience extension to access the application instance.
 */
fun android.app.Activity.aiapuriApp(): AiapuriApplication {
    return application as AiapuriApplication
}
