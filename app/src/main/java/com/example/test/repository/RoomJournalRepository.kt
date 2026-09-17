package com.example.test.repository

import com.example.test.data.JournalEntry
import com.example.test.data.JournalEntryDao
import kotlinx.coroutines.flow.Flow

class RoomJournalRepository(private val dao: JournalEntryDao) : JournalRepository {
    override fun getAll(): Flow<List<JournalEntry>> = dao.getAll()

    override fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>> =
        dao.getSince(sinceEpochMillis)

    override suspend fun getById(id: Long): JournalEntry? = dao.getById(id)

    override suspend fun save(entry: JournalEntry): Long {
        return if (entry.id == 0L) {
            dao.insert(entry)
        } else {
            dao.update(entry)
            entry.id
        }
    }

    override suspend fun delete(entry: JournalEntry) = dao.delete(entry)
}
