package com.aiapuri.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aiapuri.core.model.AppSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * Manages app-lock state.
 *
 * When app lock is enabled, the user must authenticate (biometric or device
 * credential) before accessing the main app screens.
 *
 * [checkAuthAvailable] reports whether the device supports biometric/device
 * credential authentication.
 */
class AppLockManager(
    private val activity: FragmentActivity,
    private val appSettingsFlow: Flow<AppSettings>
) {

    /** Whether the device has biometric or device-credential hardware. */
    fun isAuthAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Prompt the user to authenticate.
     *
     * Emits `true` on success, `false` on error/cancel.
     *
     * Note: setNegativeButtonText() must NOT be used when DEVICE_CREDENTIAL
     * is included in setAllowedAuthenticators — the system handles cancellation
     * for device credentials itself. Violating this throws IllegalArgumentException.
     */
    fun authenticate(): Flow<Boolean> = callbackFlow {
        val promptInfo = try {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock AI-apuri")
                .setSubtitle("Authenticate to access your conversations")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        } catch (_: Exception) {
            // Prompt configuration failed — emit error and close
            trySend(false)
            close()
            return@callbackFlow
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                trySend(true)
                close()
            }

            override fun onAuthenticationFailed() {
                // User tried but failed — allow retry, don't close yet
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                trySend(false)
                close()
            }
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                callback
            )
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {
            // Authentication setup failed — emit error and close
            trySend(false)
            close()
            return@callbackFlow
        }

        awaitClose {
            // If the flow is cancelled (e.g. nav change), treat as auth failure
            trySend(false)
        }
    }

    /**
     * Flow that emits `true` when app lock is enabled AND the device supports
     * authentication. If either condition is false, emits `false`.
     */
    val shouldShowLockScreen: Flow<Boolean> = appSettingsFlow.map { settings ->
        settings.appLockEnabled && isAuthAvailable()
    }
}
