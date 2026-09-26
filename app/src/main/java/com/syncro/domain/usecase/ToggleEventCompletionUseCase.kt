package com.syncro.domain.usecase

import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

class ToggleEventCompletionUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(eventId: String) {
        repository.toggleEventCompletion(eventId)
        googleSyncRepository.updateEventInGoogle(eventId)
    }
}
