package com.syncro.domain.usecase

import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

class ToggleSubtaskCompletionUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(eventId: String, subtaskTitle: String) {
        repository.toggleSubtaskCompletion(eventId, subtaskTitle)
        // El estado de las subtareas vive en la descripción del evento en Google ([x] / [ ])
        googleSyncRepository.pushEvent(eventId)
    }
}
