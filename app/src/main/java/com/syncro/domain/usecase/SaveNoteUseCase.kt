package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.NoteRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

class SaveNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    // Reloj inyectado en lugar de LocalDateTime.now(): en los tests se usa uno fijo
    private val clock: Clock
) {
    /**
     * Crea (sin [id]) o edita una nota. Falla sin guardar nada con [BlankTitleException] si el
     * título está vacío. Al editar se conserva la fecha de creación: es la que ordena la lista.
     */
    suspend operator fun invoke(
        id: String? = null,
        title: String,
        content: String,
        color: ArgbColor
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(BlankTitleException())

        val existing = id?.let { repository.getNoteById(it) }
        val note = SyncroItem.Note(
            id = id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            content = content,
            color = color,
            createdAt = existing?.createdAt ?: LocalDateTime.now(clock)
        )
        repository.insertNote(note)
        return Result.success(Unit)
    }
}
