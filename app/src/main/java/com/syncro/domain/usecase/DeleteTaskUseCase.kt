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
    suspend operator fun invoke(taskId: String) {
        repository.deleteTask(taskId)
        googleSyncRepository.pushTask(taskId)
    }
}
