package com.example.test.ui.trend

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoodTrendViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun points_mapsMoodAndIntensityToScore() = runTest {
        val repository = FakeJournalRepository()
        val now = System.currentTimeMillis()
        repository.save(
            JournalEntry(createdAt = now, updatedAt = now, text = "Great day", mood = Mood.GREAT, intensity = 5),
        )

        val viewModel = MoodTrendViewModel(repository)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.points.collect() }

        val point = viewModel.points.value.single()
        assertEquals(now, point.timestampMillis)
        assertEquals(5f, point.score, 0.01f)

        collectJob.cancel()
    }
}
