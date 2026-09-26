package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.TaskRepository
import javax.inject.Inject

class ToggleTaskCompletionUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(taskId: String) {
        repository.toggleTaskCompletion(taskId)
        googleSyncRepository.pushTask(taskId)
    }
}
