package com.example.test.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.security.AppLockPreferences
import com.example.test.security.BiometricAuthenticator

@Composable
fun AppLockGate(
    activity: FragmentActivity,
    appLockPreferences: AppLockPreferences,
    biometricAuthenticator: BiometricAuthenticator,
    viewModel: AppLockViewModel = viewModel(
        factory = viewModelFactory { initializer { AppLockViewModel(appLockPreferences) } },
    ),
    content: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.locked) {
        val promptAuthentication = {
            biometricAuthenticator.authenticate(
                activity = activity,
                onSuccess = viewModel::onAuthenticated,
                onFailure = {},
            )
        }
        LaunchedEffect(Unit) { promptAuthentication() }
        LockScreen(onUnlockClick = promptAuthentication)
    } else {
        content()
    }
}

@Composable
private fun LockScreen(onUnlockClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text("Mood Journal is locked", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Unlock")
            }
        }
    }
}
