package com.syncro.presentation.notes

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.DeleteNoteUseCase
import com.syncro.domain.usecase.GetNotesUseCase
import com.syncro.domain.usecase.SaveNoteUseCase
import com.syncro.presentation.theme.toArgbColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    getNotesUseCase: GetNotesUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase
) : ViewModel() {

    val notes: StateFlow<List<SyncroItem.Note>> = getNotesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveNote(id: String?, title: String, content: String, color: Color) {
        viewModelScope.launch {
            saveNoteUseCase(id = id, title = title, content = content, color = color.toArgbColor())
        }
    }

    fun deleteNote(note: SyncroItem.Note) {
        viewModelScope.launch {
            deleteNoteUseCase(note.id)
        }
    }
}
