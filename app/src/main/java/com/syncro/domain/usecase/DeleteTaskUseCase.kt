package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Borra una tarea: desaparece de la app al momento y después se borra en Google Tasks. Sin
 * conexión, el borrado queda pendiente y se completa al recuperarla (la tarea no vuelve a salir).
 */
class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    /**
     * Con [uploadNow] a false el borrado se queda solo en el móvil (pendiente) para poder
     * deshacerlo; se sube con la siguiente subida de pendientes (`PushPendingChangesUseCase`).
     */
    suspend operator fun invoke(taskId: String, uploadNow: Boolean = true) {
        repository.deleteTask(taskId)
        if (uploadNow) googleSyncRepository.pushTask(taskId)
    }
}
