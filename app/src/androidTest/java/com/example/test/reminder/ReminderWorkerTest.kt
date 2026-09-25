package com.example.test.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.test.data.AppDatabase
import com.example.test.data.JournalEntry
import com.example.test.data.JournalEntryDao
import com.example.test.data.Mood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao: JournalEntryDao = AppDatabase.getInstance(context).journalEntryDao()

    private fun startOfTodayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private suspend fun deleteTodaysEntries() {
        dao.getSince(startOfTodayMillis()).first().forEach { dao.delete(it) }
    }

    @Before
    fun clearTodaysEntriesBefore() = runBlocking { deleteTodaysEntries() }

    @After
    fun clearTodaysEntriesAfter() = runBlocking { deleteTodaysEntries() }

    // Notifications posted by one test method persist (in the notification manager) into the
    // next, since AndroidJUnitRunner runs all methods in this class in the same process without
    // resetting notification state between them. Clear NOTIFICATION_ID on both sides of each test
    // so the two tests don't leak state into each other regardless of execution order.
    @Before
    fun clearReminderNotificationBefore() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @After
    fun clearReminderNotificationAfter() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @Test
    fun doWork_postsNotification_whenNoEntryToday() = runBlocking {
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val notifications = NotificationManagerCompat.from(context).activeNotifications
        assertTrue(notifications.any { it.id == NOTIFICATION_ID })
    }

    @Test
    fun doWork_skipsNotification_whenEntryExistsToday() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insert(
            JournalEntry(createdAt = now, updatedAt = now, text = "Already journaled", mood = Mood.OKAY, intensity = 3),
        )

        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val notifications = NotificationManagerCompat.from(context).activeNotifications
        assertTrue(notifications.none { it.id == NOTIFICATION_ID })
    }
}
