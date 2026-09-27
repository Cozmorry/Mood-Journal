package com.example.test.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// androidx.biometric's AuthenticatorUtils.isSupportedCombination() reports
// BIOMETRIC_STRONG or DEVICE_CREDENTIAL as unsupported on API 28-29 (a platform
// BiometricPrompt bug on those two OS versions) -- BiometricManager.canAuthenticate()
// returns BIOMETRIC_ERROR_UNSUPPORTED there, and PromptInfo.Builder.build() throws
// IllegalArgumentException if used anyway. BIOMETRIC_WEAK or DEVICE_CREDENTIAL is
// supported on every API level down to this app's minSdk, so fall back to WEAK there.
private fun allowedAuthenticators(): Int =
    if (Build.VERSION.SDK_INT in 28..29) {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

class SystemBiometricAuthenticator(private val context: Context) : BiometricAuthenticator {
    override fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit) {
        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Mood Journal")
                .setAllowedAuthenticators(allowedAuthenticators())
                .build()

            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onFailure()
                    }

                    override fun onAuthenticationFailed() {
                        onFailure()
                    }
                },
            )

            prompt.authenticate(promptInfo)
        } catch (e: IllegalArgumentException) {
            onFailure()
        }
    }
}
