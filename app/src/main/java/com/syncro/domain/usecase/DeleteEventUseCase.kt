package com.syncro.domain.usecase

import com.syncro.domain.model.RepeatScope
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.RepeatSeriesRepository
import javax.inject.Inject

/**
 * Borra un evento: desaparece de la app al momento y después se borra en Google Calendar. Sin
 * conexión, el borrado queda pendiente y se completa al recuperarla (el evento no vuelve a salir).
 */
class DeleteEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository,
    private val seriesRepository: RepeatSeriesRepository
) {
    /**
     * Con [uploadNow] a false el borrado se queda solo en el móvil (pendiente) para poder
     * deshacerlo; se sube con la siguiente subida de pendientes (`PushPendingChangesUseCase`).
     *
     * En un evento que se repite, [RepeatScope.THIS_AND_FOLLOWING] borra también las siguientes
     * repeticiones y corta la serie para que no se creen más.
     */
    suspend operator fun invoke(eventId: String, uploadNow: Boolean = true, scope: RepeatScope = RepeatScope.THIS) {
        val event = repository.getEventById(eventId)
        val seriesId = event?.seriesId
        if (scope == RepeatScope.THIS_AND_FOLLOWING && event != null && seriesId != null) {
            seriesRepository.endBefore(seriesId, event.date)
            repository.getEventIdsInSeries(seriesId, event.date).plus(eventId).distinct().forEach { repository.deleteEvent(it) }
            if (uploadNow) googleSyncRepository.pushPendingChanges()
            return
        }
        repository.deleteEvent(eventId)
        if (uploadNow) googleSyncRepository.pushEvent(eventId)
    }
}
