package com.example.test.reminder

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

/**
 * WARNING: this test deletes today's journal entries from whatever database it runs
 * against — `AppDatabase.getInstance(applicationContext)` is the real, on-device
 * database, not an isolated in-memory one (see the design doc's Architecture section
 * for why `ReminderWorker` doesn't take an injectable repository). Only ever
 * run `connectedAndroidTest`/this test against a clean, disposable emulator — never a
 * personal device with real journal data.
 */
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

    // Without this, POST_NOTIFICATIONS isn't granted on a fresh API 33+ emulator/device,
    // so ReminderWorker's own permission guard silently no-ops postNotification() and
    // both tests below stop exercising the real notification-posting path: the "posts"
    // test fails loudly, but the "skips" test can't distinguish a correctly-skipped
    // notification from one that was simply never allowed to post, regardless of the
    // skip logic actually being exercised. Granting it here makes both deterministic.
    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
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
