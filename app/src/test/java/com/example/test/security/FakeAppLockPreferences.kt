package com.example.test.security

class FakeAppLockPreferences(
    private var enabled: Boolean = false,
) : AppLockPreferences {
    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
