package com.example.test.ui.editor

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import com.example.test.repository.FakePhotoStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EntryEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveDisabled_whenTextBlank() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)

        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("   ")
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("Feeling good today")
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_persistsEntryToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = 0L)

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
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = entryId)

        viewModel.delete()

        assertTrue(repository.currentEntries.isEmpty())
    }

    @Test
    fun onPhotoPicked_updatesPhotoPathInState() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)

        viewModel.onPhotoPicked("photo_1.jpg")

        assertEquals("photo_1.jpg", viewModel.uiState.value.photoPath)
    }

    @Test
    fun onPhotoRemoved_clearsPhotoPath() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)
        viewModel.onPhotoPicked("photo_1.jpg")

        viewModel.onPhotoRemoved()

        assertNull(viewModel.uiState.value.photoPath)
    }

    @Test
    fun save_persistsPhotoPathToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = 0L)

        viewModel.onTextChange("Beach day")
        viewModel.onPhotoPicked("photo_1.jpg")
        viewModel.save()

        assertEquals("photo_1.jpg", repository.currentEntries[0].photoPath)
    }

    @Test
    fun save_deletesOldPhotoFileWhenReplaced() = runTest {
        val repository = FakeJournalRepository()
        val entryId = repository.save(
            JournalEntry(
                createdAt = 1L,
                updatedAt = 1L,
                text = "Beach day",
                mood = Mood.GOOD,
                intensity = 3,
                photoPath = "old_photo.jpg",
            ),
        )
        val photoStorage = FakePhotoStorage()
        val viewModel = EntryEditorViewModel(repository, photoStorage, entryId = entryId)

        viewModel.onPhotoPicked("new_photo.jpg")
        viewModel.save()

        assertEquals(listOf("old_photo.jpg"), photoStorage.deletedPaths)
        assertEquals("new_photo.jpg", repository.currentEntries[0].photoPath)
    }

    @Test
    fun delete_deletesPhotoFileForExistingEntry() = runTest {
        val repository = FakeJournalRepository()
        val entryId = repository.save(
            JournalEntry(
                createdAt = 1L,
                updatedAt = 1L,
                text = "Beach day",
                mood = Mood.GOOD,
                intensity = 3,
                photoPath = "photo_1.jpg",
            ),
        )
        val photoStorage = FakePhotoStorage()
        val viewModel = EntryEditorViewModel(repository, photoStorage, entryId = entryId)

        viewModel.delete()

        assertEquals(listOf("photo_1.jpg"), photoStorage.deletedPaths)
    }
}
