package com.syncro.presentation.notes

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    val notes: StateFlow<List<SyncroItem.Note>> = noteRepository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveNote(id: String?, title: String, content: String, color: Color) {
        viewModelScope.launch {
            val note = SyncroItem.Note(
                id = id ?: UUID.randomUUID().toString(),
                title = title,
                content = content,
                color = color,
                createdAt = LocalDateTime.now()
            )
            noteRepository.insertNote(note)
        }
    }

    fun deleteNote(note: SyncroItem.Note) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
        }
    }
}
