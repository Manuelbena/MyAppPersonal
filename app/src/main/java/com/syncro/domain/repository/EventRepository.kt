package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface EventRepository {
    fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>>
    suspend fun insertEvent(event: SyncroItem.Event, date: LocalDate, location: String? = null)
    suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String)
    suspend fun toggleEventCompletion(eventId: String)
}
