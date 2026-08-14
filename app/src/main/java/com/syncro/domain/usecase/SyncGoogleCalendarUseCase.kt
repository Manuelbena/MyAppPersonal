package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

class SyncGoogleCalendarUseCase @Inject constructor(
    private val repository: GoogleSyncRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.syncCalendar()
    }
}
