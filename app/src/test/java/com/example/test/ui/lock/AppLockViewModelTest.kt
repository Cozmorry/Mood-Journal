package com.example.test.ui.lock

import com.example.test.security.FakeAppLockPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockViewModelTest {
    @Test
    fun initialState_lockedWhenPreferenceEnabled() {
        val viewModel = AppLockViewModel(FakeAppLockPreferences(enabled = true))

        assertTrue(viewModel.uiState.value.locked)
    }

    @Test
    fun initialState_unlockedWhenPreferenceDisabled() {
        val viewModel = AppLockViewModel(FakeAppLockPreferences(enabled = false))

        assertFalse(viewModel.uiState.value.locked)
    }

    @Test
    fun onAuthenticated_unlocks() {
        val viewModel = AppLockViewModel(FakeAppLockPreferences(enabled = true))

        viewModel.onAuthenticated()

        assertFalse(viewModel.uiState.value.locked)
    }
}
