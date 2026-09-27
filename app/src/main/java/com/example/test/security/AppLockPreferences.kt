package com.example.test.security

interface AppLockPreferences {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
