package com.example.test.reminder

interface ReminderPreferences {
    fun isEnabled(): Boolean
    fun getTime(): Pair<Int, Int> // hour (0-23), minute (0-59)
    fun setReminder(enabled: Boolean, hour: Int, minute: Int)
}
