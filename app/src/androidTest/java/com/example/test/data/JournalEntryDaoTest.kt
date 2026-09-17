package com.example.test.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalEntryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: JournalEntryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.journalEntryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndGetAll_returnsEntriesNewestFirst() = runBlocking {
        dao.insert(JournalEntry(createdAt = 1L, updatedAt = 1L, text = "First", mood = Mood.OKAY, intensity = 3))
        dao.insert(JournalEntry(createdAt = 2L, updatedAt = 2L, text = "Second", mood = Mood.GOOD, intensity = 4))

        val entries = dao.getAll().first()

        assertEquals(listOf("Second", "First"), entries.map { it.text })
    }

    @Test
    fun update_changesExistingEntry() = runBlocking {
        val id = dao.insert(
            JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Original", mood = Mood.OKAY, intensity = 3),
        )
        val updated = dao.getById(id)!!.copy(text = "Updated", mood = Mood.GREAT, intensity = 5)

        dao.update(updated)

        val result = dao.getById(id)
        assertEquals("Updated", result?.text)
        assertEquals(Mood.GREAT, result?.mood)
    }

    @Test
    fun delete_removesEntry() = runBlocking {
        val entry = JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Temp", mood = Mood.BAD, intensity = 2)
        val id = dao.insert(entry)
        val saved = dao.getById(id)!!

        dao.delete(saved)

        assertNull(dao.getById(id))
    }

    @Test
    fun getSince_excludesOlderEntries() = runBlocking {
        dao.insert(JournalEntry(createdAt = 100L, updatedAt = 100L, text = "Old", mood = Mood.OKAY, intensity = 3))
        dao.insert(JournalEntry(createdAt = 500L, updatedAt = 500L, text = "Recent", mood = Mood.GOOD, intensity = 4))

        val entries = dao.getSince(sinceEpochMillis = 200L).first()

        assertEquals(listOf("Recent"), entries.map { it.text })
    }
}
