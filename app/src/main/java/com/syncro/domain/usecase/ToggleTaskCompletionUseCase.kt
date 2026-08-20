package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.TaskRepository
import javax.inject.Inject

class ToggleTaskCompletionUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(taskId: String) {
        val task = repository.getTaskById(taskId)
        repository.toggleTaskCompletion(taskId)
        
        // Si tiene remoteId, actualizar en Google Tasks
        task?.remoteId?.let { remoteId ->
            googleSyncRepository.updateTaskInGoogle(
                remoteId = remoteId,
                title = task.title,
                notes = task.description,
                isCompleted = !task.isCompleted // Invertimos porque acabamos de hacer el toggle
            )
        }
    }
}
