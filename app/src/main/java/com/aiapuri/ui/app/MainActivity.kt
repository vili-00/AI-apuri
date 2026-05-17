@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiapuri.ui.app

import androidx.compose.material3.ExperimentalMaterial3Api

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.aiapuri.AiapuriApplication
import com.aiapuri.ui.theme.AIapuriTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Main Activity hosting the Compose navigation graph.
 *
 * Handles screenshot blocking (FLAG_SECURE) based on user settings.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply screenshot blocking based on saved settings
        applyScreenshotProtection()

        setContent {
            AIapuriTheme {
                AppNavigation()
            }
        }
    }

    /**
     * Read the block-screenshots setting and apply FLAG_SECURE if enabled.
     */
    private fun applyScreenshotProtection() {
        val app = application as AiapuriApplication
        runBlocking {
            val blockScreenshots = app.settingsRepository.appSettingsFlow.first().blockScreenshots
            if (blockScreenshots) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
