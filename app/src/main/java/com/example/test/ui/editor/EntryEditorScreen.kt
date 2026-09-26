package com.example.test.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    entryId: Long,
    onDone: () -> Unit,
    onViewPhoto: (String) -> Unit,
    viewModel: EntryEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryEditorViewModel(repository, photoStorage, entryId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tagSuggestions by viewModel.existingTagSuggestions.collectAsStateWithLifecycle()
    val filteredTagSuggestions = remember(uiState.tagInput, tagSuggestions, uiState.tags) {
        tagSuggestions
            .filter { it.contains(uiState.tagInput, ignoreCase = true) && it !in uiState.tags }
            .take(5)
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingCameraFilename by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    viewModel.onPhotoPicked(photoStorage.copyFrom(uri))
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Couldn't add that photo")
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            pendingCameraFilename?.let { viewModel.onPhotoPicked(it) }
        }
        pendingCameraFilename = null
    }

    fun launchCameraCapture() {
        coroutineScope.launch {
            try {
                val capture = photoStorage.newCameraCapture()
                pendingCameraFilename = capture.filename
                cameraLauncher.launch(capture.uri)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Couldn't open the camera")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            launchCameraCapture()
        } else {
            cameraPermissionDenied = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val photoPath = uiState.photoPath
                if (photoPath != null) {
                    Box {
                        AsyncImage(
                            model = photoStorage.resolve(photoPath),
                            contentDescription = "Attached photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(
                                    selected = false,
                                    onClick = { onViewPhoto(photoPath) },
                                ),
                        )
                        IconButton(
                            onClick = viewModel::onPhotoRemoved,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo")
                        }
                    }
                }
                IconButton(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Add from gallery")
                }
                IconButton(onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        launchCameraCapture()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Take photo")
                }
            }
            if (cameraPermissionDenied) {
                Text(
                    "Camera permission needed to take a photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
            OutlinedTextField(
                value = uiState.tagInput,
                onValueChange = { newValue ->
                    if (newValue.contains(",")) {
                        viewModel.onTagCommitted(newValue.substringBefore(","))
                        viewModel.onTagInputChange(newValue.substringAfter(","))
                    } else {
                        viewModel.onTagInputChange(newValue)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = { Text("Add a tag") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onTagCommitted(uiState.tagInput) }),
            )
            if (uiState.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.onTagRemoved(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove tag $tag",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            if (filteredTagSuggestions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredTagSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.onTagCommitted(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
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
