package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface EventRepository {
    fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>>
    fun getEventsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>>
    /** Crea o sustituye el evento con ese id (el id lo asigna el dominio) y sus subtareas. */
    suspend fun insertEvent(event: SyncroItem.Event)
    suspend fun getEventById(eventId: String): SyncroItem.Event?
    suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String)
    suspend fun toggleEventCompletion(eventId: String)
}
