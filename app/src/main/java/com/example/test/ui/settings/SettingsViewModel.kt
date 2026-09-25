package com.example.test.ui.settings

import androidx.lifecycle.ViewModel
import com.example.test.reminder.ReminderPreferences
import com.example.test.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val permissionDenied: Boolean = false,
)

class SettingsViewModel(
    private val reminderPreferences: ReminderPreferences,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        run {
            val (hour, minute) = reminderPreferences.getTime()
            SettingsUiState(
                reminderEnabled = reminderPreferences.isEnabled(),
                reminderHour = hour,
                reminderMinute = minute,
            )
        },
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onPermissionGranted() {
        val state = _uiState.value
        _uiState.value = state.copy(reminderEnabled = true, permissionDenied = false)
        reminderPreferences.setReminder(true, state.reminderHour, state.reminderMinute)
        reminderScheduler.schedule(state.reminderHour, state.reminderMinute)
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(reminderEnabled = false, permissionDenied = true)
    }

    fun onReminderDisabled() {
        val state = _uiState.value
        _uiState.value = state.copy(reminderEnabled = false)
        reminderPreferences.setReminder(false, state.reminderHour, state.reminderMinute)
        reminderScheduler.cancel()
    }

    fun onTimeChanged(hour: Int, minute: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(reminderHour = hour, reminderMinute = minute)
        reminderPreferences.setReminder(state.reminderEnabled, hour, minute)
        if (state.reminderEnabled) {
            reminderScheduler.schedule(hour, minute)
        }
    }
}
