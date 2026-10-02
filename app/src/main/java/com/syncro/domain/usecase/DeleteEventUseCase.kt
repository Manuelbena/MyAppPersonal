package com.syncro.domain.usecase

import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

/**
 * Borra un evento: desaparece de la app al momento y después se borra en Google Calendar. Sin
 * conexión, el borrado queda pendiente y se completa al recuperarla (el evento no vuelve a salir).
 */
class DeleteEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(eventId: String) {
        repository.deleteEvent(eventId)
        googleSyncRepository.pushEvent(eventId)
    }
}
