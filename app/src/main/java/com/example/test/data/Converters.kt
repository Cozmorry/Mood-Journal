package com.example.test.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun moodToString(mood: Mood): String = mood.name

    @TypeConverter
    fun stringToMood(value: String): Mood = Mood.valueOf(value)

    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(",")

    @TypeConverter
    fun stringToTags(value: String): List<String> = splitTags(value)
}

internal fun splitTags(raw: String): List<String> =
    if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
