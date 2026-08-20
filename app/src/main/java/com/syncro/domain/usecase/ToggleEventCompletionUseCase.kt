package com.syncro.domain.usecase

import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

class ToggleEventCompletionUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(eventId: String) {
        val event = repository.getEventById(eventId)
        repository.toggleEventCompletion(eventId)
        
        // Si tiene remoteId, actualizar en Google Calendar
        event?.remoteId?.let { remoteId ->
            googleSyncRepository.updateEventInGoogle(
                remoteId = remoteId,
                title = event.title,
                description = event.description,
                isCompleted = !event.isCompleted // Invertimos porque acabamos de hacer el toggle localmente
            )
        }
    }
}
