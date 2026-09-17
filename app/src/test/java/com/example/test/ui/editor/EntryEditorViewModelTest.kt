package com.example.test.ui.editor

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EntryEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveDisabled_whenTextBlank() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), entryId = 0L)

        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("   ")
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("Feeling good today")
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_persistsEntryToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, entryId = 0L)

        viewModel.onTextChange("Feeling good today")
        viewModel.onMoodChange(Mood.GREAT)
        viewModel.onIntensityChange(5)
        viewModel.save()

        val entries = repository.currentEntries
        assertEquals(1, entries.size)
        assertEquals("Feeling good today", entries[0].text)
        assertEquals(Mood.GREAT, entries[0].mood)
        assertEquals(5, entries[0].intensity)
    }

    @Test
    fun delete_removesExistingEntry() = runTest {
        val repository = FakeJournalRepository()
        val entryId = repository.save(
            JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Old", mood = Mood.OKAY, intensity = 2),
        )
        val viewModel = EntryEditorViewModel(repository, entryId = entryId)

        viewModel.delete()

        assertTrue(repository.currentEntries.isEmpty())
    }
}
