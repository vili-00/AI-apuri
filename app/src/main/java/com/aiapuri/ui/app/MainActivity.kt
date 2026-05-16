@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiapuri.ui.app

import androidx.compose.material3.ExperimentalMaterial3Api

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aiapuri.ui.theme.AIapuriTheme

/**
 * Main Activity hosting the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIapuriTheme {
                AppNavigation()
            }
        }
    }
}
