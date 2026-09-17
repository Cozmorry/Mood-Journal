package com.example.test.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
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
                    val isSelected = mood == uiState.mood
                    val emojiStyle = if (isSelected) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    }
                    val backgroundColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Column(
                        modifier = Modifier
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                onClick = { viewModel.onMoodChange(mood) },
                                role = Role.RadioButton,
                            )
                            .background(backgroundColor)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = mood.emoji, style = emojiStyle, color = contentColor)
                        Text(text = mood.label, style = MaterialTheme.typography.labelSmall, color = contentColor)
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
