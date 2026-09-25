package com.example.test.reminder

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "reminder_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_HOUR = "hour"
private const val KEY_MINUTE = "minute"
private const val DEFAULT_HOUR = 20
private const val DEFAULT_MINUTE = 0

class SharedPreferencesReminderPreferences(context: Context) : ReminderPreferences {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun getTime(): Pair<Int, Int> =
        prefs.getInt(KEY_HOUR, DEFAULT_HOUR) to prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)

    override fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit {
            putBoolean(KEY_ENABLED, enabled)
            putInt(KEY_HOUR, hour)
            putInt(KEY_MINUTE, minute)
        }
    }
}
