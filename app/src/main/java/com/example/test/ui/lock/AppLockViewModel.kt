package com.example.test.ui.lock

import androidx.lifecycle.ViewModel
import com.example.test.security.AppLockPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLockUiState(val locked: Boolean)

class AppLockViewModel(appLockPreferences: AppLockPreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(AppLockUiState(locked = appLockPreferences.isEnabled()))
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    fun onAuthenticated() {
        _uiState.value = _uiState.value.copy(locked = false)
    }
}
