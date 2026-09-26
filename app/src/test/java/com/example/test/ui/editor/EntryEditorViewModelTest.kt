package com.example.test.ui.editor

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import com.example.test.repository.FakePhotoStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun onTagCommitted_addsTagAndClearsInput() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)

        viewModel.onTagInputChange("work")
        viewModel.onTagCommitted("work")

        assertEquals(listOf("work"), viewModel.uiState.value.tags)
        assertEquals("", viewModel.uiState.value.tagInput)
    }

    @Test
    fun onTagCommitted_blankInput_isIgnored() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)

        viewModel.onTagCommitted("   ")

        assertTrue(viewModel.uiState.value.tags.isEmpty())
    }

    @Test
    fun onTagCommitted_duplicateOnSameEntry_isIgnored() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)
        viewModel.onTagCommitted("work")

        viewModel.onTagCommitted("work")

        assertEquals(listOf("work"), viewModel.uiState.value.tags)
    }

    @Test
    fun onTagRemoved_removesTagFromState() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)
        viewModel.onTagCommitted("work")
        viewModel.onTagCommitted("stressed")

        viewModel.onTagRemoved("work")

        assertEquals(listOf("stressed"), viewModel.uiState.value.tags)
    }

    @Test
    fun save_persistsTagsToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = 0L)

        viewModel.onTextChange("Busy day")
        viewModel.onTagCommitted("work")
        viewModel.onTagCommitted("stressed")
        viewModel.save()

        assertEquals(listOf("work", "stressed"), repository.currentEntries[0].tags)
    }

    @Test
    fun existingTagSuggestions_reflectsTagsFromOtherEntries() = runTest {
        val repository = FakeJournalRepository()
        repository.save(
            JournalEntry(
                createdAt = 1L,
                updatedAt = 1L,
                text = "Old",
                mood = Mood.OKAY,
                intensity = 3,
                tags = listOf("work", "family"),
            ),
        )
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = 0L)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.existingTagSuggestions.collect() }

        assertEquals(listOf("family", "work"), viewModel.existingTagSuggestions.value)

        collectJob.cancel()
    }
}
