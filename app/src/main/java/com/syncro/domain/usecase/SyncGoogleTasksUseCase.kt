package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

class SyncGoogleTasksUseCase @Inject constructor(
    private val repository: GoogleSyncRepository
) {
    suspend operator fun invoke(force: Boolean = false): Result<Unit> {
        return repository.syncTasks(force)
    }
}
