package com.example.test.reminder

interface ReminderScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}
