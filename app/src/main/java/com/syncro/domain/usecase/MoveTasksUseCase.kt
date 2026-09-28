package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.TaskRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Pasa tareas a otro día, manteniendo su hora. Primero se guardan todas en local (la app responde
 * al instante y sin conexión) y después se suben a Google; si la subida falla queda pendiente.
 */
class MoveTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(taskIds: List<String>, date: LocalDate) {
        taskIds.forEach { repository.moveTask(it, date) }
        taskIds.forEach { googleSyncRepository.pushTask(it) }
    }
}
