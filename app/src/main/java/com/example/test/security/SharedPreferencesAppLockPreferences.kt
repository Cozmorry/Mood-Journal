package com.example.test.security

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "app_lock_prefs"
private const val KEY_ENABLED = "enabled"

class SharedPreferencesAppLockPreferences(context: Context) : AppLockPreferences {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_ENABLED, enabled)
        }
    }
}
