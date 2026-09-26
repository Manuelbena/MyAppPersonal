package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import java.time.LocalDate
import javax.inject.Inject

class SyncGoogleCalendarUseCase @Inject constructor(
    private val repository: GoogleSyncRepository
) {
    suspend operator fun invoke(startDate: LocalDate, endDate: LocalDate = startDate): Result<Unit> {
        return repository.syncCalendar(startDate, endDate)
    }
}
