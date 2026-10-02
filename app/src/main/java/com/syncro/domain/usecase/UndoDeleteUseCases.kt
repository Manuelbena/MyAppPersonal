package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.NoteRepository
import com.syncro.domain.repository.TaskRepository
import javax.inject.Inject

/*
 * "Deshacer" tras borrar en Inicio. Las tareas y eventos se borran sin subir a Google mientras se
 * ofrece deshacer (`DeleteTaskUseCase(id, uploadNow = false)`): deshacer es quitarles la marca de
 * borrado, con su id, lista de Google y subtareas intactos. Si el borrado ya se subió (otra
 * sincronización llegó antes), ya no se puede y devuelven false.
 */

class UndoDeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(taskId: String): Boolean {
        if (!repository.restoreTask(taskId)) return false
        // El borrado dejó un cambio pendiente: se sube el estado actual (el de antes de borrar)
        googleSyncRepository.pushTask(taskId)
        return true
    }
}

class UndoDeleteEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(eventId: String): Boolean {
        if (!repository.restoreEvent(eventId)) return false
        googleSyncRepository.pushEvent(eventId)
        return true
    }
}

/** Las notas solo viven en el móvil: deshacer es volver a guardarla tal cual (mismo id y fecha). */
class UndoDeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(note: SyncroItem.Note) = repository.insertNote(note)
}
