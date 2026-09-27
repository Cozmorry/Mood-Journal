package com.example.test.security

import androidx.fragment.app.FragmentActivity

interface BiometricAuthenticator {
    fun isAvailable(): Boolean
    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit)
}
