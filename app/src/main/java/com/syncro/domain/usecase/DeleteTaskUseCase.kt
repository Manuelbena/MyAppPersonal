package com.syncro.domain.usecase

import com.syncro.domain.model.RepeatScope
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.RepeatSeriesRepository
import com.syncro.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Borra una tarea: desaparece de la app al momento y después se borra en Google Tasks. Sin
 * conexión, el borrado queda pendiente y se completa al recuperarla (la tarea no vuelve a salir).
 */
class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository,
    private val seriesRepository: RepeatSeriesRepository
) {
    /**
     * Con [uploadNow] a false el borrado se queda solo en el móvil (pendiente) para poder
     * deshacerlo; se sube con la siguiente subida de pendientes (`PushPendingChangesUseCase`).
     *
     * En una tarea que se repite, [RepeatScope.THIS_AND_FOLLOWING] borra también las siguientes
     * repeticiones y corta la serie para que no se creen más.
     */
    suspend operator fun invoke(taskId: String, uploadNow: Boolean = true, scope: RepeatScope = RepeatScope.THIS) {
        val task = repository.getTaskById(taskId)
        val seriesId = task?.seriesId
        if (scope == RepeatScope.THIS_AND_FOLLOWING && task != null && seriesId != null) {
            seriesRepository.endBefore(seriesId, task.date)
            repository.getTaskIdsInSeries(seriesId, task.date).plus(taskId).distinct().forEach { repository.deleteTask(it) }
            if (uploadNow) googleSyncRepository.pushPendingChanges()
            return
        }
        repository.deleteTask(taskId)
        if (uploadNow) googleSyncRepository.pushTask(taskId)
    }
}
