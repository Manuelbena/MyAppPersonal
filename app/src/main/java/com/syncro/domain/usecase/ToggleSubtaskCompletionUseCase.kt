package com.syncro.domain.usecase

import com.syncro.domain.repository.EventRepository
import javax.inject.Inject

class ToggleSubtaskCompletionUseCase @Inject constructor(
    private val repository: EventRepository
) {
    suspend operator fun invoke(eventId: String, subtaskTitle: String) {
        repository.toggleSubtaskCompletion(eventId, subtaskTitle)
    }
}
