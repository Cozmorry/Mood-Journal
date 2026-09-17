package com.example.test.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    repository: JournalRepository,
    entryId: Long,
    onDone: () -> Unit,
    viewModel: EntryEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryEditorViewModel(repository, entryId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isExistingEntry) "Edit entry" else "New entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isExistingEntry) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = viewModel::onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What's on your mind?") },
                minLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Mood.entries.forEach { mood ->
                    val style = if (mood == uiState.mood) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    }
                    TextButton(onClick = { viewModel.onMoodChange(mood) }) {
                        Text(text = mood.emoji, style = style)
                    }
                }
            }
            Text("Intensity: ${uiState.intensity}", modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = uiState.intensity.toFloat(),
                onValueChange = { viewModel.onIntensityChange(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
            )
            Button(
                onClick = viewModel::save,
                enabled = uiState.isSaveEnabled,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Save")
            }
        }
    }
}
