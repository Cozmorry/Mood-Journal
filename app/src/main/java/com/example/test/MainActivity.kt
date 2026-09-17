package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.test.data.AppDatabase
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
        setContent {
            MoodJournalTheme {
                MoodJournalNavHost(repository = repository)
            }
        }
    }
}
