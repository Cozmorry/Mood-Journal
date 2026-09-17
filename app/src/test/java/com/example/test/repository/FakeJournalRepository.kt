package com.example.test.repository

import com.example.test.data.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeJournalRepository : JournalRepository {
    private val entriesFlow = MutableStateFlow<List<JournalEntry>>(emptyList())
    private var nextId = 1L

    val currentEntries: List<JournalEntry> get() = entriesFlow.value

    override fun getAll(): Flow<List<JournalEntry>> = entriesFlow.asStateFlow()

    override fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>> =
        MutableStateFlow(entriesFlow.value.filter { it.createdAt >= sinceEpochMillis }).asStateFlow()

    override suspend fun getById(id: Long): JournalEntry? =
        entriesFlow.value.find { it.id == id }

    override suspend fun save(entry: JournalEntry): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        val saved = entry.copy(id = id)
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id } + saved
        return id
    }

    override suspend fun delete(entry: JournalEntry) {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entry.id }
    }
}
