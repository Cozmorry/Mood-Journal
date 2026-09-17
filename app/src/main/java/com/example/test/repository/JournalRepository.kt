package com.example.test.repository

import com.example.test.data.JournalEntry
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    fun getAll(): Flow<List<JournalEntry>>
    fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>>
    suspend fun getById(id: Long): JournalEntry?
    suspend fun save(entry: JournalEntry): Long
    suspend fun delete(entry: JournalEntry)
}
