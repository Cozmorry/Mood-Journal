package com.example.test.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

data class MoodPoint(val timestampMillis: Long, val score: Float)

private const val TREND_WINDOW_DAYS = 30L

class MoodTrendViewModel(repository: JournalRepository) : ViewModel() {
    val points: StateFlow<List<MoodPoint>> = repository
        .getSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TREND_WINDOW_DAYS))
        .map { entries ->
            entries.map { entry ->
                val moodScore = (Mood.entries.size - 1 - entry.mood.ordinal).toFloat()
                MoodPoint(
                    timestampMillis = entry.createdAt,
                    score = moodScore + entry.intensity / 5f,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
