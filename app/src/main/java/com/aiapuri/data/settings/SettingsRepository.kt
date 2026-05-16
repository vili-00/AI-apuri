package com.aiapuri.data.settings

import com.aiapuri.core.model.AppSettings
import com.aiapuri.core.model.ServerSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persistent application settings.
 *
 * Non-secret values are stored via DataStore.
 * Sensitive values (API key) are encrypted at rest.
 */
interface SettingsRepository {

    /** Reactive stream of server settings. */
    val serverSettingsFlow: Flow<ServerSettings>

    /** Reactive stream of app settings. */
    val appSettingsFlow: Flow<AppSettings>

    /** Save server settings. */
    suspend fun saveServerSettings(settings: ServerSettings)

    /** Save app settings. */
    suspend fun saveAppSettings(settings: AppSettings)

    /** Mark onboarding as completed. */
    suspend fun markOnboardingCompleted()

    /** Check whether onboarding has been completed. */
    suspend fun hasCompletedOnboarding(): Boolean
}
