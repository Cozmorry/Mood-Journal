package com.example.test.ui.list

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
class EntryListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun entries_reflectsRepositoryData() = runTest {
        val repository = FakeJournalRepository()
        repository.save(
            JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Hi", mood = Mood.GOOD, intensity = 3),
        )
        val viewModel = EntryListViewModel(repository)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.entries.collect() }

        assertEquals(listOf("Hi"), viewModel.entries.value.map { it.text })

        collectJob.cancel()
    }
}
