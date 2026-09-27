package com.example.test.ui.settings

import com.example.test.reminder.FakeReminderPreferences
import com.example.test.reminder.FakeReminderScheduler
import com.example.test.security.FakeAppLockPreferences
import com.example.test.security.FakeBiometricAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    private fun viewModel(
        reminderPreferences: FakeReminderPreferences = FakeReminderPreferences(),
        reminderScheduler: FakeReminderScheduler = FakeReminderScheduler(),
        appLockPreferences: FakeAppLockPreferences = FakeAppLockPreferences(),
        biometricAuthenticator: FakeBiometricAuthenticator = FakeBiometricAuthenticator(),
    ) = SettingsViewModel(reminderPreferences, reminderScheduler, appLockPreferences, biometricAuthenticator)

    @Test
    fun initialState_reflectsStoredPreferences() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 7, minute = 30)
        val viewModel = viewModel(reminderPreferences = preferences)

        assertTrue(viewModel.uiState.value.reminderEnabled)
        assertEquals(7, viewModel.uiState.value.reminderHour)
        assertEquals(30, viewModel.uiState.value.reminderMinute)
    }

    @Test
    fun onPermissionGranted_enablesAndSchedules() {
        val preferences = FakeReminderPreferences()
        val scheduler = FakeReminderScheduler()
        val viewModel = viewModel(reminderPreferences = preferences, reminderScheduler = scheduler)

        viewModel.onPermissionGranted()

        assertTrue(viewModel.uiState.value.reminderEnabled)
        assertTrue(preferences.isEnabled())
        assertEquals(20 to 0, scheduler.scheduledTime)
    }

    @Test
    fun onPermissionDenied_leavesDisabledAndShowsMessage() {
        val preferences = FakeReminderPreferences()
        val scheduler = FakeReminderScheduler()
        val viewModel = viewModel(reminderPreferences = preferences, reminderScheduler = scheduler)

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
        val viewModel = viewModel(reminderPreferences = preferences, reminderScheduler = scheduler)

        viewModel.onReminderDisabled()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertFalse(preferences.isEnabled())
        assertEquals(1, scheduler.cancelCallCount)
    }

    @Test
    fun onTimeChanged_whileEnabled_reschedules() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = viewModel(reminderPreferences = preferences, reminderScheduler = scheduler)

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
        val viewModel = viewModel(reminderPreferences = preferences, reminderScheduler = scheduler)

        viewModel.onTimeChanged(7, 45)

        assertEquals(7 to 45, preferences.getTime())
        assertNull(scheduler.scheduledTime)
    }

    @Test
    fun initialState_reflectsStoredAppLockPreference() {
        val viewModel = viewModel(appLockPreferences = FakeAppLockPreferences(enabled = true))

        assertTrue(viewModel.uiState.value.appLockEnabled)
    }

    @Test
    fun onAppLockToggled_on_whenAvailable_persistsAndEnables() {
        val appLockPreferences = FakeAppLockPreferences()
        val viewModel = viewModel(
            appLockPreferences = appLockPreferences,
            biometricAuthenticator = FakeBiometricAuthenticator(available = true),
        )

        viewModel.onAppLockToggled(true)

        assertTrue(viewModel.uiState.value.appLockEnabled)
        assertFalse(viewModel.uiState.value.appLockUnavailable)
        assertTrue(appLockPreferences.isEnabled())
    }

    @Test
    fun onAppLockToggled_on_whenUnavailable_staysOffAndShowsMessage() {
        val appLockPreferences = FakeAppLockPreferences()
        val viewModel = viewModel(
            appLockPreferences = appLockPreferences,
            biometricAuthenticator = FakeBiometricAuthenticator(available = false),
        )

        viewModel.onAppLockToggled(true)

        assertFalse(viewModel.uiState.value.appLockEnabled)
        assertTrue(viewModel.uiState.value.appLockUnavailable)
        assertFalse(appLockPreferences.isEnabled())
    }

    @Test
    fun onAppLockToggled_off_persistsDisabled() {
        val appLockPreferences = FakeAppLockPreferences(enabled = true)
        val viewModel = viewModel(appLockPreferences = appLockPreferences)

        viewModel.onAppLockToggled(false)

        assertFalse(viewModel.uiState.value.appLockEnabled)
        assertFalse(appLockPreferences.isEnabled())
    }
}
