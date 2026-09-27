package com.example.test.security

import androidx.fragment.app.FragmentActivity

class FakeBiometricAuthenticator(
    private val available: Boolean = true,
    private val succeeds: Boolean = true,
) : BiometricAuthenticator {
    var authenticateCallCount = 0
        private set

    override fun isAvailable(): Boolean = available

    override fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit) {
        authenticateCallCount++
        if (succeeds) onSuccess() else onFailure()
    }
}
