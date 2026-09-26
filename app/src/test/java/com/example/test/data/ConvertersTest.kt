package com.example.test.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun tagsToString_joinsWithComma() {
        assertEquals("work,stressed", converters.tagsToString(listOf("work", "stressed")))
    }

    @Test
    fun tagsToString_emptyList_producesEmptyString() {
        assertEquals("", converters.tagsToString(emptyList()))
    }

    @Test
    fun stringToTags_splitsOnComma_andTrims() {
        assertEquals(listOf("work", "stressed"), converters.stringToTags("work, stressed"))
    }

    @Test
    fun stringToTags_emptyString_producesEmptyList() {
        assertEquals(emptyList<String>(), converters.stringToTags(""))
    }

    @Test
    fun stringToTags_blankString_producesEmptyList() {
        assertEquals(emptyList<String>(), converters.stringToTags("   "))
    }

    @Test
    fun stringToTags_ignoresBlankSegments() {
        assertEquals(listOf("work"), converters.stringToTags("work,,  "))
    }

    @Test
    fun roundTrip_preservesTags() {
        val original = listOf("work", "family", "stressed")
        val roundTripped = converters.stringToTags(converters.tagsToString(original))
        assertEquals(original, roundTripped)
    }

    @Test
    fun aggregateDistinctTags_flattensDedupesAndSorts() {
        val result = aggregateDistinctTags(listOf(listOf("work", "stressed"), listOf("stressed", "family")))
        assertEquals(listOf("family", "stressed", "work"), result)
    }

    @Test
    fun aggregateDistinctTags_emptyInput_producesEmptyList() {
        assertEquals(emptyList<String>(), aggregateDistinctTags(emptyList()))
    }

    @Test
    fun aggregateDistinctTags_entriesWithNoTags_areIgnored() {
        val result = aggregateDistinctTags(listOf(emptyList(), listOf("work"), emptyList()))
        assertEquals(listOf("work"), result)
    }
}
