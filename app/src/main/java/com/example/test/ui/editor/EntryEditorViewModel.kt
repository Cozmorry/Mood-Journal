package com.example.test.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EntryEditorUiState(
    val entryId: Long = 0L,
    val createdAt: Long = 0L,
    val text: String = "",
    val mood: Mood = Mood.OKAY,
    val intensity: Int = 3,
    val isExistingEntry: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isSaveEnabled: Boolean get() = text.isNotBlank()
}

class EntryEditorViewModel(
    private val repository: JournalRepository,
    entryId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EntryEditorUiState(entryId = entryId))
    val uiState: StateFlow<EntryEditorUiState> = _uiState.asStateFlow()

    private var saveInFlight = false

    init {
        if (entryId != 0L) {
            viewModelScope.launch {
                repository.getById(entryId)?.let { entry ->
                    _uiState.value = EntryEditorUiState(
                        entryId = entry.id,
                        createdAt = entry.createdAt,
                        text = entry.text,
                        mood = entry.mood,
                        intensity = entry.intensity,
                        isExistingEntry = true,
                    )
                }
            }
        }
    }

    fun onTextChange(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun onMoodChange(mood: Mood) {
        _uiState.value = _uiState.value.copy(mood = mood)
    }

    fun onIntensityChange(intensity: Int) {
        _uiState.value = _uiState.value.copy(intensity = intensity)
    }

    fun save() {
        val state = _uiState.value
        if (!state.isSaveEnabled || saveInFlight) return
        saveInFlight = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.save(
                JournalEntry(
                    id = state.entryId,
                    createdAt = if (state.isExistingEntry) state.createdAt else now,
                    updatedAt = now,
                    text = state.text,
                    mood = state.mood,
                    intensity = state.intensity,
                ),
            )
            _uiState.value = state.copy(isSaved = true)
        }
    }

    fun delete() {
        val state = _uiState.value
        if (!state.isExistingEntry) return
        viewModelScope.launch {
            repository.delete(
                JournalEntry(
                    id = state.entryId,
                    createdAt = state.createdAt,
                    updatedAt = state.createdAt,
                    text = state.text,
                    mood = state.mood,
                    intensity = state.intensity,
                ),
            )
            _uiState.value = state.copy(isSaved = true)
        }
    }
}
