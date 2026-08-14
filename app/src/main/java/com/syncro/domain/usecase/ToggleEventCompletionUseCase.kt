package com.syncro.domain.usecase

import com.syncro.domain.repository.EventRepository
import javax.inject.Inject

class ToggleEventCompletionUseCase @Inject constructor(
    private val repository: EventRepository
) {
    suspend operator fun invoke(eventId: String) {
        repository.toggleEventCompletion(eventId)
    }
}
