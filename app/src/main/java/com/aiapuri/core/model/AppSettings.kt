package com.aiapuri.core.model

import java.time.Instant

/**
 * General application preferences (non-secret).
 */
data class AppSettings(
    val hasCompletedOnboarding: Boolean = false,
    val darkTheme: Boolean? = null,  // null = follow system
    val appLockEnabled: Boolean = false,
    val blockScreenshots: Boolean = false,
    val autoGenerateTitles: Boolean = true
)
