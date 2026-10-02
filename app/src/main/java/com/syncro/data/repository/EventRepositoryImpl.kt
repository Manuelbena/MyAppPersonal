package com.syncro.data.repository

import com.syncro.data.local.toLocalTimeOrMidnight
import com.syncro.data.local.toStoredTime
import com.syncro.domain.model.ArgbColor
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.EventWithSubtasks
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao
) : EventRepository {

    override fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>> {
        return dao.getEventsByDate(date.toEpochDay()).map { relations ->
            relations.map { it.toDomain() }
        }
    }

    override fun getEventsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>> {
        return dao.getEventsInRange(startDate.toEpochDay(), endDate.toEpochDay()).map { relations ->
            relations.map { it.toDomain() }
        }
    }

    override suspend fun insertEvent(event: SyncroItem.Event) {
        require(event.id.isNotBlank()) { "El id del evento lo asigna el dominio" }
        val eventId = event.id
        val existing = dao.getEventById(eventId)
        val entity = EventEntity(
            id = eventId,
            remoteId = event.remoteId ?: existing?.remoteId,
            title = event.title,
            description = event.description,
            date = event.date.toEpochDay(),
            endDate = event.endDate.toEpochDay(),
            startTime = event.startTime.toStoredTime(),
            endTime = event.endTime.toStoredTime(),
            categoryText = event.categoryText,
            categoryColor = event.categoryColor.argb,
            priority = event.priority?.name,
            location = event.location,
            isCompleted = event.isCompleted,
            pendingChanges = (existing?.pendingChanges ?: 0) + 1
        )

        // insertEventWithSubtasks siempre limpia las subtareas previas antes de reinsertar,
        // incluso con lista vacía: evita depender del borrado en cascada implícito de REPLACE.
        val subtaskEntities = event.subtasks.map {
            SubtaskEntity(eventId = eventId, title = it.title, isCompleted = it.isCompleted)
        }
        dao.insertEventWithSubtasks(entity, subtaskEntities)
    }

    override suspend fun getEventById(eventId: String): SyncroItem.Event? {
        // Un evento borrado ya no existe para la app, aunque siga en la tabla hasta borrarse en Google
        return dao.getEventById(eventId)?.takeUnless { it.isDeleted }?.let { entity ->
            val subtasks = dao.getSubtasksForEvent(eventId)
            EventWithSubtasks(entity, subtasks).toDomain()
        }
    }

    private fun EventWithSubtasks.toDomain(): SyncroItem.Event {
        val subtasks = subtasks.map { Subtask(title = it.title, isCompleted = it.isCompleted) }
        return SyncroItem.Event(
            id = event.id,
            remoteId = event.remoteId,
            title = event.title,
            description = event.description,
            date = LocalDate.ofEpochDay(event.date),
            endDate = LocalDate.ofEpochDay(event.endDate),
            startTime = event.startTime.toLocalTimeOrMidnight(),
            endTime = event.endTime.toLocalTimeOrMidnight(),
            categoryText = event.categoryText,
            categoryColor = ArgbColor(event.categoryColor),
            priority = event.priority?.let { Priority.valueOf(it) },
            subtasks = subtasks,
            isCompleted = event.isCompleted,
            location = event.location
        )
    }

    override suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        dao.toggleSubtaskCompletion(eventId, subtaskTitle)
    }

    override suspend fun toggleEventCompletion(eventId: String) {
        dao.toggleEventCompletion(eventId)
    }

    override suspend fun deleteEvent(eventId: String) {
        dao.markEventDeleted(eventId)
    }
}
