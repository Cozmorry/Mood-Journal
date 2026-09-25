package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.test.data.AppDatabase
import com.example.test.reminder.EXTRA_OPEN_NEW_ENTRY
import com.example.test.reminder.SharedPreferencesReminderPreferences
import com.example.test.reminder.WorkManagerReminderScheduler
import com.example.test.repository.FilePhotoStorage
import com.example.test.repository.RoomJournalRepository
import com.example.test.ui.MoodJournalNavHost
import com.example.test.ui.theme.MoodJournalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = RoomJournalRepository(
            AppDatabase.getInstance(applicationContext).journalEntryDao(),
        )
        val photoStorage = FilePhotoStorage(applicationContext)
        val reminderPreferences = SharedPreferencesReminderPreferences(applicationContext)
        val reminderScheduler = WorkManagerReminderScheduler(applicationContext)
        val startAtNewEntry = intent?.getBooleanExtra(EXTRA_OPEN_NEW_ENTRY, false) ?: false
        setContent {
            MoodJournalTheme {
                MoodJournalNavHost(
                    repository = repository,
                    photoStorage = photoStorage,
                    reminderPreferences = reminderPreferences,
                    reminderScheduler = reminderScheduler,
                    startAtNewEntry = startAtNewEntry,
                )
            }
        }
    }
}
