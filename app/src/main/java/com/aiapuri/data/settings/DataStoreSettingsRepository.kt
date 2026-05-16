package com.aiapuri.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiapuri.core.model.AppSettings
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.core.security.EncryptedStringStorage
import com.aiapuri.core.util.ServerUrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed SettingsRepository implementation.
 *
 * Non-secret values are stored in DataStore Preferences.
 * The API key is encrypted via [EncryptedStringStorage] before being persisted.
 */
class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {

    private val encryptedStorage = EncryptedStringStorage(context)

    companion object {
        private const val SETTINGS_PREFS_NAME = "aiapuri_settings"

        private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
            name = SETTINGS_PREFS_NAME
        )

        // ---- DataStore keys ----
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_ALLOW_NO_API_KEY = booleanPreferencesKey("allow_no_api_key")
        private val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme")  // "true", "false", or absent
        private val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        private val KEY_BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")

        // ---- Encrypted prefs key ----
        private const val ENCRYPTED_KEY_API_KEY = "api_key"
    }

    private val dataStore = context.settingsDataStore

    // ==================== Server Settings ====================

    override val serverSettingsFlow: Flow<ServerSettings> =
        dataStore.data.map { prefs ->
            val rawBaseUrl = prefs[KEY_BASE_URL] ?: ""
            val normalizedBaseUrl = if (rawBaseUrl.isNotBlank()) {
                ServerUrlValidator.normalize(rawBaseUrl)
            } else {
                ""
            }
            val apiKey = encryptedStorage.decryptAndRetrieve(ENCRYPTED_KEY_API_KEY)
            val allowNoApiKey = prefs[KEY_ALLOW_NO_API_KEY] ?: false
            val defaultModel = prefs[KEY_DEFAULT_MODEL]

            ServerSettings(
                baseUrl = normalizedBaseUrl,
                apiKey = apiKey,
                allowNoApiKey = allowNoApiKey,
                defaultModel = defaultModel
            )
        }

    override suspend fun saveServerSettings(settings: ServerSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = settings.baseUrl
            prefs[KEY_ALLOW_NO_API_KEY] = settings.allowNoApiKey
            prefs[KEY_DEFAULT_MODEL] = settings.defaultModel ?: ""
        }
        // Encrypt and store API key separately
        encryptedStorage.encryptAndStore(ENCRYPTED_KEY_API_KEY, settings.apiKey)
    }

    // ==================== App Settings ====================

    override val appSettingsFlow: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            val onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false
            val darkThemeStr = prefs[KEY_DARK_THEME]
            val darkTheme = when (darkThemeStr) {
                "true" -> true
                "false" -> false
                else -> null
            }
            val appLock = prefs[KEY_APP_LOCK] ?: false
            val blockScreenshots = prefs[KEY_BLOCK_SCREENSHOTS] ?: false

            AppSettings(
                hasCompletedOnboarding = onboardingDone,
                darkTheme = darkTheme,
                appLockEnabled = appLock,
                blockScreenshots = blockScreenshots
            )
        }

    override suspend fun saveAppSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE] = settings.hasCompletedOnboarding
            prefs[KEY_DARK_THEME] = settings.darkTheme?.toString() ?: ""
            prefs[KEY_APP_LOCK] = settings.appLockEnabled
            prefs[KEY_BLOCK_SCREENSHOTS] = settings.blockScreenshots
        }
    }

    // ==================== Onboarding helpers ====================

    override suspend fun markOnboardingCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE] = true
        }
    }

    override suspend fun hasCompletedOnboarding(): Boolean {
        return dataStore.data
            .map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }
            .first()
    }
}
