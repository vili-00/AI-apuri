package com.aiapuri.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiapuri.AiapuriApplication
import com.aiapuri.core.security.AppLockManager
import com.aiapuri.core.util.findFragmentActivity

/**
 * Full-screen lock shown when app lock is enabled.
 *
 * Prompts the user for biometric/device-credential authentication.
 * On success, calls [onAuthenticated] which should navigate to the main app.
 *
 * If biometric authentication is unavailable or the activity context is not
 * a FragmentActivity, a safe fallback is shown that lets the user skip and
 * disable app lock so the app is never permanently bricked.
 */
@Composable
fun AppLockScreen(
    onAuthenticated: () -> Unit,
    onDisableAppLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as AiapuriApplication
    val activity = LocalContext.current.findFragmentActivity()

    // Determine which mode to run in
    val mode = when {
        activity == null -> LockMode.Unavailable
        else -> LockMode.Biometric
    }

    var authResult by remember { mutableStateOf<AuthResult?>(null) }

    // ---- Biometric mode ----
    if (mode == LockMode.Biometric) {
        // activity is non-null here because mode == Biometric implies it passed the null check
        val safeActivity = activity!!
        val appLockManager = remember {
            AppLockManager(
                activity = safeActivity,
                appSettingsFlow = application.settingsRepository.appSettingsFlow
            )
        }

        // Trigger authentication on first compose
        LaunchedEffect(Unit) {
            appLockManager.authenticate().collect { success ->
                authResult = if (success) AuthResult.Granted else AuthResult.Denied
            }
        }

        // React to result
        LaunchedEffect(authResult) {
            when (authResult) {
                is AuthResult.Granted -> onAuthenticated()
                else -> Unit // Denied or null — stay on lock screen
            }
        }

        BiometricLockContent(
            authResult = authResult,
            onSkipAndDisable = onDisableAppLock,
            modifier = modifier
        )
    } else {
        // ---- Fallback mode: biometric unavailable ----
        FallbackLockContent(
            onSkipAndDisable = onDisableAppLock,
            modifier = modifier
        )
    }
}

/**
 * Content shown when biometric prompt is available.
 */
@Composable
private fun BiometricLockContent(
    authResult: AuthResult?,
    onSkipAndDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "AI-apuri is locked",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = "Authenticate to access your conversations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Show loading indicator while waiting for biometric prompt
                if (authResult == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 24.dp).size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                // Skip & disable button — always visible as a safety net
                TextButton(
                    onClick = onSkipAndDisable,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Skip & disable app lock")
                }
            }
        }
    }
}

/**
 * Fallback content shown when biometric is unavailable
 * (e.g. no FragmentActivity context, no hardware).
 */
@Composable
private fun FallbackLockContent(
    onSkipAndDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.warningColor()
                )

                Text(
                    text = "App lock unavailable",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = "Biometric authentication is not available on this device " +
                            "or in the current context. App lock will be disabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Button(
                    onClick = onSkipAndDisable,
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Continue without app lock")
                }
            }
        }
    }
}

/**
 * Internal helper to get a warm warning color.
 */
private fun androidx.compose.material3.ColorScheme.warningColor(): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(0xFFFFA000)
}

private sealed class AuthResult {
    object Granted : AuthResult()
    object Denied : AuthResult()
}

/**
 * Determines which lock mode to use.
 */
private enum class LockMode {
    Biometric,
    Unavailable
}
