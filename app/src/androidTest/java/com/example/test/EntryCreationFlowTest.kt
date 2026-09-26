package com.example.test

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class EntryCreationFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun creatingEntry_showsItInList() {
        // Unique text/tag avoids collisions with entries left over from earlier test runs
        // in the app's real on-device database (this test doesn't use an isolated DB).
        val timestamp = System.currentTimeMillis()
        val entryText = "Had a great walk today $timestamp"
        val uniqueTag = "tag$timestamp"

        composeRule.onNodeWithContentDescription("New entry").performClick()
        composeRule.onNodeWithText("What's on your mind?").performTextInput(entryText)
        composeRule.onNodeWithText("Add a tag").performTextInput("$uniqueTag,")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText(entryText).assertExists()
        composeRule.onNodeWithText("#$uniqueTag").assertExists()
    }
}
