package com.example.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val text: String,
    val mood: Mood,
    val intensity: Int,
    val photoPath: String? = null,
    val tags: List<String> = emptyList(),
)
