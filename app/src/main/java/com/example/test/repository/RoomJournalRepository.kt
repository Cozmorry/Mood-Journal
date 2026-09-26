package com.example.test.repository

import com.example.test.data.JournalEntry
import com.example.test.data.JournalEntryDao
import com.example.test.data.aggregateDistinctTags
import com.example.test.data.splitTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomJournalRepository(private val dao: JournalEntryDao) : JournalRepository {
    override fun getAll(): Flow<List<JournalEntry>> = dao.getAll()

    override fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>> =
        dao.getSince(sinceEpochMillis)

    override fun getAllTags(): Flow<List<String>> =
        dao.getAllTagsRaw().map { raw -> aggregateDistinctTags(raw.map(::splitTags)) }

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
