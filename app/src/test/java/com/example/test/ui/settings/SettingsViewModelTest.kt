package com.example.test.ui.settings

import com.example.test.reminder.FakeReminderPreferences
import com.example.test.reminder.FakeReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun initialState_reflectsStoredPreferences() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 7, minute = 30)
        val viewModel = SettingsViewModel(preferences, FakeReminderScheduler())

        assertTrue(viewModel.uiState.value.reminderEnabled)
        assertEquals(7, viewModel.uiState.value.reminderHour)
        assertEquals(30, viewModel.uiState.value.reminderMinute)
    }

    @Test
    fun onPermissionGranted_enablesAndSchedules() {
        val preferences = FakeReminderPreferences()
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onPermissionGranted()

        assertTrue(viewModel.uiState.value.reminderEnabled)
        assertTrue(preferences.isEnabled())
        assertEquals(20 to 0, scheduler.scheduledTime)
    }

    @Test
    fun onPermissionDenied_leavesDisabledAndShowsMessage() {
        val preferences = FakeReminderPreferences()
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onPermissionDenied()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertTrue(viewModel.uiState.value.permissionDenied)
        assertNull(scheduler.scheduledTime)
        assertFalse(preferences.isEnabled())
    }

    @Test
    fun onReminderDisabled_persistsAndCancels() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onReminderDisabled()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertFalse(preferences.isEnabled())
        assertEquals(1, scheduler.cancelCallCount)
    }

    @Test
    fun onTimeChanged_whileEnabled_reschedules() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onTimeChanged(7, 45)

        assertEquals(7, viewModel.uiState.value.reminderHour)
        assertEquals(45, viewModel.uiState.value.reminderMinute)
        assertEquals(7 to 45, scheduler.scheduledTime)
        assertEquals(7 to 45, preferences.getTime())
    }

    @Test
    fun onTimeChanged_whileDisabled_persistsButDoesNotSchedule() {
        val preferences = FakeReminderPreferences(enabled = false, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onTimeChanged(7, 45)

        assertEquals(7 to 45, preferences.getTime())
        assertNull(scheduler.scheduledTime)
    }
}
