package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface EventRepository {
    /** Eventos que tocan [date], incluidos los que empezaron antes (filtrar con Event.occursOn). */
    fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>>
    fun getEventsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>>
    /** Crea o sustituye el evento con ese id (el id lo asigna el dominio) y sus subtareas. */
    suspend fun insertEvent(event: SyncroItem.Event)
    suspend fun getEventById(eventId: String): SyncroItem.Event?
    suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String)
    suspend fun toggleEventCompletion(eventId: String)
    /** Lo quita de la app al momento; el borrado en Google queda pendiente de subir. */
    suspend fun deleteEvent(eventId: String)

    /** Deshace [deleteEvent] si el borrado aún no se ha subido a Google; false si ya no se puede. */
    suspend fun restoreEvent(eventId: String): Boolean
    /** Ids de las repeticiones de la serie que empiezan desde [from] incluido (sin las borradas). */
    suspend fun getEventIdsInSeries(seriesId: String, from: LocalDate): List<String>
    /** Los que tienen aviso y aún pueden sonar: sin completar, sin borrar, que empiezan desde [from]. */
    fun observeEventsWithReminder(from: LocalDate): Flow<List<SyncroItem.Event>>
}
