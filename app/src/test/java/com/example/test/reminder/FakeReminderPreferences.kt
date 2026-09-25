package com.example.test.reminder

class FakeReminderPreferences(
    private var enabled: Boolean = false,
    private var hour: Int = 20,
    private var minute: Int = 0,
) : ReminderPreferences {
    override fun isEnabled(): Boolean = enabled

    override fun getTime(): Pair<Int, Int> = hour to minute

    override fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        this.enabled = enabled
        this.hour = hour
        this.minute = minute
    }
}
