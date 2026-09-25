package com.example.test.reminder

class FakeReminderScheduler : ReminderScheduler {
    var scheduledTime: Pair<Int, Int>? = null
        private set
    var cancelCallCount = 0
        private set

    override fun schedule(hour: Int, minute: Int) {
        scheduledTime = hour to minute
    }

    override fun cancel() {
        cancelCallCount++
        scheduledTime = null
    }
}
