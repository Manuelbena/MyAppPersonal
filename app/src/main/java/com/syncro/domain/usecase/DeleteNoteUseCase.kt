package com.syncro.domain.usecase

import com.syncro.domain.repository.NoteRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(noteId: String) = repository.deleteNote(noteId)
}
