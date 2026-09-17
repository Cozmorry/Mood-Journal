package com.example.test.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun moodToString(mood: Mood): String = mood.name

    @TypeConverter
    fun stringToMood(value: String): Mood = Mood.valueOf(value)
}
